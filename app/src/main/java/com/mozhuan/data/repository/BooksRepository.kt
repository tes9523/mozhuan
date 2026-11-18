package com.mozhuan.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import com.mozhuan.data.model.TrashedNovel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 书稿仓库：`book/<书名>/` 下每本小说可包含「小说资料」和「章节内容」。
 *
 * - 新建的小说会补齐完整的标准目录。
 * - 导入的小说只会创建「章节内容/」，AI 分析时再去创建其它目录。
 * - 删除走回收站：`book/.trash/<时间戳>_<书名>/`，二次删除才彻底清除。
 * - 挂载的外部目录下的小说直接扫描出来，删除 / 回收站都不支持。
 */
class BooksRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val context = context.applicationContext
    private val root = File(context.filesDir, "book").apply { mkdirs() }
    private val trashRoot = File(root, ".trash").apply { mkdirs() }

    private val _books = MutableStateFlow<List<Novel>>(emptyList())
    val books: StateFlow<List<Novel>> = _books.asStateFlow()

    private val _trash = MutableStateFlow<List<TrashedNovel>>(emptyList())
    val trash: StateFlow<List<TrashedNovel>> = _trash.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        root.mkdirs()
        val novels = mutableListOf<Novel>()
        novels += root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && isNovelDirectory(it) }
            .map { dir -> localNovel(dir) }
        novels += mountedNovels()
        _books.value = novels.sortedByDescending { it.updatedAt }

        _trash.value = trashRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith(TRASH_PREFIX) }
            .mapNotNull { parseTrashDir(it) }
            .sortedByDescending { it.trashedAt }
    }

    private fun localNovel(dir: File): Novel {
        val meta = readMeta(dir)
        return Novel(
            id = dir.name,
            title = meta.optString("title").ifBlank { dir.name },
            coverIndex = meta.optInt("cover", 0),
            customCover = meta.optString("coverPath").ifBlank { null },
            chapterCount = countChapters(dir),
            updatedAt = dir.lastModified(),
        )
    }

    private fun parseTrashDir(dir: File): TrashedNovel? {
        val rest = dir.name.removePrefix(TRASH_PREFIX)
        val split = rest.indexOf('_')
        val ts = rest.substring(0, split).toLongOrNull() ?: return null
        val title = rest.substring(split + 1).ifBlank { dir.name }
        val originalMeta = runCatching {
            JSONObject(File(dir, META_FILE).takeIf { it.isFile }?.readText().orEmpty())
        }.getOrDefault(JSONObject())
        return TrashedNovel(
            id = dir.name,
            title = originalMeta.optString("title").ifBlank { title },
            originalPath = originalMeta.optString("originalPath").ifBlank { "book/$title" },
            trashedAt = ts,
            totalBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
        )
    }

    fun createBook(title: String, coverIndex: Int): Novel {
        val name = safeTitle(title.ifBlank { "未命名作品" })
        var dir = File(root, name)
        var index = 2
        while (dir.exists()) {
            dir = File(root, "$name (${index++})")
        }
        dir.mkdirs()
        writeMeta(dir, title = name, coverIndex = coverIndex, coverPath = null)
        ensureStandardStructure(dir)
        reload()
        return _books.value.first { it.id == dir.name }
    }

    /** AI 创建的小说：补齐标准目录（与用户新建一致）。 */
    fun ensureStructure(bookId: String) {
        if (isMountedBook(bookId)) return
        val dir = File(root, bookId)
        if (!dir.isDirectory) return
        ensureStandardStructure(dir)
        dir.setLastModified(System.currentTimeMillis())
    }

    private fun ensureStandardStructure(dir: File) {
        STANDARD_FOLDERS.forEach { File(dir, it).mkdirs() }
        // 根目录散落的正文文件归入「章节内容」。
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in TEXT_EXTENSIONS }
            .forEach { file ->
                val target = File(File(dir, "章节内容"), file.name)
                if (!target.exists()) file.renameTo(target)
            }
    }

    /** 导入的小说只建章节内容；小说资料由 AI 按需创建。 */
    private fun ensureImportStructure(dir: File) {
        File(dir, "章节内容").mkdirs()
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in TEXT_EXTENSIONS }
            .forEach { file ->
                val target = File(File(dir, "章节内容"), file.name)
                if (!target.exists()) file.renameTo(target)
            }
    }

    /** 进入小说目录前的补齐逻辑：本地书补齐标准结构，挂载书只读。 */
    fun ensureBookOnEnter(bookId: String) {
        if (isMountedBook(bookId)) return
        ensureStructure(bookId)
    }

    fun listEntries(bookId: String, relativePath: String): List<NovelEntry> {
        if (isMountedBook(bookId)) return listMountedEntries(bookId, relativePath)
        val dir = resolveLocal(bookId, relativePath) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .map { file ->
                val wordCount = if (file.isFile && file.extension.lowercase() in TEXT_EXTENSIONS) {
                    runCatching { file.readText().length }.getOrDefault(0)
                } else 0
                NovelEntry(
                    name = file.name,
                    path = if (relativePath.isBlank()) file.name else "$relativePath/${file.name}",
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    wordCount = wordCount,
                    updatedAt = file.lastModified(),
                )
            }
            .sortedWith(compareByDescending<NovelEntry> { it.isDirectory }.thenBy { it.name })
    }

    fun readFile(bookId: String, relativePath: String): String? {
        if (isMountedBook(bookId)) return readMountedFile(bookId, relativePath)
        val file = resolveLocal(bookId, relativePath) ?: return null
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun writeFile(bookId: String, relativePath: String, content: String): Boolean {
        if (isMountedBook(bookId)) return writeMountedFile(bookId, relativePath, content)
        val file = resolveLocal(bookId, relativePath) ?: return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            File(root, bookId).setLastModified(System.currentTimeMillis())
            true
        }.getOrDefault(false)
    }

    /** 上传封面：把图片存到 `<book>/.cover/` 并更新 meta。 */
    fun setCoverFromGallery(bookId: String, uri: Uri): String? {
        if (isMountedBook(bookId)) return null
        val bookDir = File(root, bookId)
        if (!bookDir.isDirectory) return null
        val coverDir = File(bookDir, ".cover").apply { mkdirs() }
        val target = File(coverDir, "cover_${System.currentTimeMillis()}.jpg")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
                true
            } ?: false
        }.getOrDefault(false)
        if (!ok) return null
        // 清理旧的 cover 文件
        coverDir.listFiles()?.filter { it.name != target.name }?.forEach { it.delete() }
        val meta = readMeta(bookDir).put("coverPath", ".cover/${target.name}")
        File(bookDir, META_FILE).writeText(meta.toString())
        reload()
        return ".cover/${target.name}"
    }

    fun setBuiltInCover(bookId: String, coverIndex: Int) {
        if (isMountedBook(bookId)) return
        val bookDir = File(root, bookId)
        if (!bookDir.isDirectory) return
        runCatching { File(bookDir, ".cover").deleteRecursively() }
        writeMeta(bookDir, title = readMeta(bookDir).optString("title").ifBlank { bookId }, coverIndex = coverIndex, coverPath = null)
        reload()
    }

    fun coverFile(bookId: String): File? {
        if (isMountedBook(bookId)) return null
        val bookDir = File(root, bookId)
        val meta = readMeta(bookDir)
        val path = meta.optString("coverPath").ifBlank { null } ?: return null
        return File(bookDir, path).takeIf { it.isFile }
    }

    /** 软删除 → 回收站。挂载目录不支持回收，直接跳过。 */
    fun moveToTrash(bookId: String): Boolean {
        if (isMountedBook(bookId)) return false
        val src = File(root, bookId)
        if (!src.isDirectory) return false
        val ts = System.currentTimeMillis()
        val target = File(trashRoot, "${TRASH_PREFIX}${ts}_${src.name}")
        // 把原路径与 meta 写入一份，恢复时需要。
        runCatching {
            JSONObject(src.resolve(META_FILE).takeIf { it.isFile }?.readText().orEmpty())
                .put("originalPath", "book/${src.name}")
                .put("trashedAt", ts)
                .toString()
                .let { target.writeText(it) }
        }
        val ok = src.renameTo(target)
        reload()
        return ok
    }

    fun restoreFromTrash(trashId: String): Boolean {
        val src = File(trashRoot, trashId)
        if (!src.isDirectory) return false
        val bookId = trashId.removePrefix(TRASH_PREFIX).substringAfter('_').ifBlank { src.name }
        var target = File(root, bookId)
        var i = 2
        while (target.exists()) target = File(root, "$bookId ($i++)")
        val ok = src.renameTo(target)
        reload()
        return ok
    }

    fun purgeTrash(trashId: String): Boolean {
        val ok = File(trashRoot, trashId).deleteRecursively()
        reload()
        return ok
    }

    fun purgeAllTrash() {
        trashRoot.listFiles()?.forEach { it.deleteRecursively() }
        reload()
    }

    fun autoPurgeExpired(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        trashRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(TRASH_PREFIX) }
            ?.forEach { dir ->
                val ts = dir.name.removePrefix(TRASH_PREFIX).substringBefore('_').toLongOrNull()
                if (ts != null && ts < cutoff) dir.deleteRecursively()
            }
    }

    fun importBook(fileName: String, bytes: ByteArray): Result<Novel> = runCatching {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_IMPORT_EXTENSIONS) { "仅支持 TXT / DOCX / MD 格式" }
        val text = when (extension) {
            "docx" -> extractDocx(bytes)
            else -> decodeText(bytes)
        }.trim()
        require(text.isNotBlank()) { "文件内容为空" }

        val title = detectTitle(text, fileName)
        val chapters = splitChapters(text)
        val name = safeTitle(title).let { n ->
            var target = File(root, n)
            var i = 2
            while (target.exists()) {
                target = File(root, "$n ($i++)")
            }
            target.name
        }
        val dir = File(root, name).apply { mkdirs() }
        writeMeta(dir, title = name, coverIndex = 0, coverPath = null)
        // 导入：只建「章节内容」，小说资料交给 AI 按需创建。
        ensureImportStructure(dir)
        val chapterDir = File(dir, "章节内容")
        chapters.forEachIndexed { index, chapter ->
            val fileNameSafe = "%03d %s.md".format(index + 1, safeFileName(chapter.title))
            File(chapterDir, fileNameSafe).writeText(chapter.body)
        }
        if (chapters.isEmpty()) File(chapterDir, "全文.md").writeText(text)
        reload()
        _books.value.first { it.id == dir.name }
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private data class Chapter(val title: String, val body: String)

    private fun isNovelDirectory(dir: File): Boolean {
        if (File(dir, META_FILE).isFile) return true
        if (File(dir, "章节内容").isDirectory) return true
        if (File(dir, "小说资料").isDirectory) return true
        return false
    }

    private fun looksLikeMountedNovel(dir: DocumentFile): Boolean {
        if (dir.findFile(META_FILE) != null) return true
        if (dir.findFile("章节内容")?.isDirectory == true) return true
        if (dir.findFile("小说资料")?.isDirectory == true) return true
        return false
    }

    private fun countChapters(dir: File): Int {
        val chapterDir = File(dir, "章节内容")
        val files = if (chapterDir.isDirectory) chapterDir.listFiles().orEmpty() else dir.listFiles().orEmpty()
        return files.count { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
    }

    private fun countMountedChapters(dir: DocumentFile): Int {
        val chapterDir = dir.findFile("章节内容")?.takeIf { it.isDirectory } ?: return 0
        return runCatching { chapterDir.listFiles() }.getOrNull().orEmpty()
            .count { it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in TEXT_EXTENSIONS }
    }

    private fun resolveLocal(bookId: String, relativePath: String): File? {
        if (bookId.isBlank() || bookId.contains("..") || bookId.contains('/')) return null
        val relative = relativePath.trim().trim('/')
        if (relative.contains("..")) return null
        return if (relative.isEmpty()) File(root, bookId) else File(File(root, bookId), relative)
    }

    private fun readMeta(dir: File): JSONObject {
        val file = File(dir, META_FILE)
        if (!file.isFile) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeMeta(dir: File, title: String, coverIndex: Int, coverPath: String?) {
        runCatching {
            File(dir, META_FILE).writeText(
                JSONObject()
                    .put("title", title)
                    .put("cover", coverIndex)
                    .apply { coverPath?.let { put("coverPath", it) } }
                    .toString(),
            )
        }
    }

    private fun detectTitle(content: String, fileName: String): String {
        Regex("《([^》\\n]{1,40})》").find(content)?.let { return it.groupValues[1].trim() }
        content.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("#")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return fileName.substringBeforeLast('.').trim().ifBlank { "未命名作品" }
    }

    private fun splitChapters(content: String): List<Chapter> {
        val matches = CHAPTER_HEADING.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        val chapters = mutableListOf<Chapter>()
        matches.forEachIndexed { index, match ->
            val bodyStart = match.range.last + 1
            val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val title = match.value.trim()
            val body = content.substring(bodyStart, bodyEnd).trim()
            chapters += Chapter(title, if (body.isEmpty()) title else "# $title\n\n$body")
        }
        return chapters
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if ('\uFFFD' !in utf8) return utf8
        return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
    }

    private fun extractDocx(bytes: ByteArray): String {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var found: String? = null
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    found = zip.readBytes().toString(Charsets.UTF_8); break
                }
                entry = zip.nextEntry
            }
            found.orEmpty()
        }
        require(xml.isNotBlank()) { "docx 中未找到正文" }
        return xml
            .replace("</w:p>", "\n")
            .replace(Regex("<w:tab[^>]*/>"), "\t")
            .replace(Regex("<w:br[^>]*/>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    // ── 挂载目录（SAF） ────────────────────────────────────────

    private fun mountedNovels(): List<Novel> {
        val mounts = settingsRepository.mountedFolders.value
        if (mounts.isEmpty()) return emptyList()
        val result = mutableListOf<Novel>()
        for (mount in mounts) {
            val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }.getOrNull() ?: continue
            val children = runCatching { tree.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                if (!child.isDirectory || !looksLikeMountedNovel(child)) continue
                val name = child.name ?: continue
                result += Novel(
                    id = mountBookId(mount.id, name),
                    title = name,
                    coverIndex = 0,
                    customCover = null,
                    chapterCount = countMountedChapters(child),
                    updatedAt = child.lastModified(),
                )
            }
        }
        return result
    }

    private fun isMountedBook(bookId: String): Boolean = bookId.startsWith(MOUNT_PREFIX)

    private fun mountBookId(mountId: String, dirName: String) = "$MOUNT_PREFIX$mountId:$dirName"
    private fun parseMountBookId(bookId: String): Pair<String, String>? {
        val rest = bookId.removePrefix(MOUNT_PREFIX)
        val idx = rest.indexOf(':')
        if (idx <= 0) return null
        return rest.substring(0, idx) to rest.substring(idx + 1)
    }

    private fun mountedRoot(bookId: String): DocumentFile? {
        val (mountId, dirName) = parseMountBookId(bookId) ?: return null
        val mount = settingsRepository.mountedFolders.value.firstOrNull { it.id == mountId } ?: return null
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }.getOrNull() ?: return null
        return runCatching { tree.findFile(dirName) }.getOrNull()?.takeIf { it.isDirectory }
    }

    private fun resolveMounted(bookId: String, relativePath: String): DocumentFile? {
        var current = mountedRoot(bookId) ?: return null
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        for (seg in segments) current = current.findFile(seg) ?: return null
        return current
    }

    private fun listMountedEntries(bookId: String, relativePath: String): List<NovelEntry> {
        val dir = (if (relativePath.isBlank()) mountedRoot(bookId) else resolveMounted(bookId, relativePath))
            ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        val items = runCatching { dir.listFiles().orEmpty() }.getOrDefault(emptyArray())
        return items
            .filter { !it.name.orEmpty().startsWith(".") }
            .mapNotNull { item ->
                val name = item.name ?: return@mapNotNull null
                NovelEntry(
                    name = name,
                    path = if (relativePath.isBlank()) name else "$relativePath/$name",
                    isDirectory = item.isDirectory,
                    size = if (item.isFile) item.length() else 0L,
                    wordCount = if (item.isFile) runCatching { item.length().toInt() }.getOrDefault(0) else 0,
                    updatedAt = item.lastModified(),
                )
            }
            .filterNotNull()
            .sortedWith(compareByDescending<NovelEntry> { it.isDirectory }.thenBy { it.name })
    }

    private fun readMountedFile(bookId: String, relativePath: String): String? {
        val file = resolveMounted(bookId, relativePath) ?: return null
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun writeMountedFile(bookId: String, relativePath: String, content: String): Boolean {
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false
        var current = mountedRoot(bookId) ?: return false
        for (seg in segments.dropLast(1)) {
            current = current.findFile(seg) ?: current.createDirectory(seg) ?: return false
        }
        val fileName = segments.last()
        val existing = current.findFile(fileName)
        val target = if (existing != null && existing.isFile) existing else current.createFile("text/plain", fileName) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)
    }

    private fun safeTitle(raw: String) =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "未命名作品" }

    private fun safeFileName(raw: String) =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").take(60).ifBlank { "未命名章节" }

    private companion object {
        const val META_FILE = ".mozhuan.json"
        const val MOUNT_PREFIX = "mount:"
        const val TRASH_PREFIX = "trash_"
        const val MAX_FILE_BYTES = 4 * 1024 * 1024L
        val TEXT_EXTENSIONS = setOf("md", "txt", "markdown")
        val SUPPORTED_IMPORT_EXTENSIONS = setOf("txt", "md", "markdown", "docx")
        val STANDARD_FOLDERS = listOf(
            "小说资料",
            "小说资料/设定",
            "小说资料/设定/世界观",
            "小说资料/设定/人物库",
            "小说资料/大纲",
            "小说资料/章节摘要",
            "章节内容",
        )
        val CHAPTER_HEADING = Regex(
            "(?m)^[ \\t]*(第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇]" +
                "(?:[ \\t　:：、.．\\-][^\\n]{0,50})?|(?i:chapter)\\s+[0-9IVXLC]+(?:[^\\n]{0,50})?)[ \\t]*$",
        )
    }
}
