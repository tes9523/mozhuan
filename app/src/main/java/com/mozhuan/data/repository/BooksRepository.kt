package com.mozhuan.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 书稿仓库：`book/<书名>/` 下每本小说固定包含「小说资料」和「章节内容」两个目录。
 *
 * ```
 * 书名/
 *   小说资料/
 *     设定/世界观、设定/人物库
 *     大纲/
 *     章节摘要/
 *   章节内容/
 * ```
 *
 * AI 对话通过 write_file 写入 book/ 的书稿也会在这里直接呈现；
 * 打开书稿时会补齐缺失的标准目录，并把根目录散落的正文文件归入「章节内容」。
 * 挂载的外部文件夹下符合小说结构的子目录也会一并显示。
 */
class BooksRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val context = context.applicationContext
    private val root = File(context.filesDir, "book").apply { mkdirs() }
    private val _books = MutableStateFlow<List<Novel>>(emptyList())
    val books: StateFlow<List<Novel>> = _books.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        root.mkdirs()
        val novels = mutableListOf<Novel>()
        novels += root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && isNovelDirectory(it) }
            .map { dir ->
                val meta = readMeta(dir)
                Novel(
                    id = dir.name,
                    title = meta.optString("title").ifBlank { dir.name },
                    coverIndex = meta.optInt("cover", 0),
                    chapterCount = countChapters(dir),
                    updatedAt = dir.lastModified(),
                )
            }
        novels += mountedNovels()
        _books.value = novels.sortedByDescending { it.updatedAt }
    }

    /** 扫描挂载的外部文件夹，把符合小说结构的子目录当作作品列出。 */
    private fun mountedNovels(): List<Novel> {
        val mounts = settingsRepository.mountedFolders.value
        if (mounts.isEmpty()) return emptyList()
        val result = mutableListOf<Novel>()
        for (mount in mounts) {
            val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }
                .getOrNull() ?: continue
            val children = runCatching { tree.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                if (!child.isDirectory) continue
                if (!looksLikeMountedNovel(child)) continue
                val name = child.name ?: continue
                result += Novel(
                    id = mountBookId(mount.id, name),
                    title = name,
                    coverIndex = 0,
                    chapterCount = countMountedChapters(child),
                    updatedAt = child.lastModified(),
                )
            }
        }
        return result
    }

    fun createBook(title: String, coverIndex: Int): Novel {
        val name = safeTitle(title.ifBlank { "未命名作品" })
        var dir = File(root, name)
        var index = 2
        while (dir.exists()) {
            dir = File(root, "$name (${index++})")
        }
        dir.mkdirs()
        writeMeta(dir, title = name, coverIndex = coverIndex)
        ensureStructure(dir.name)
        reload()
        return _books.value.first { it.id == dir.name }
    }

    /** 补齐标准目录；顺手把书稿根目录里的散装正文放进「章节内容」。 */
    fun ensureStructure(bookId: String) {
        if (isMountedBook(bookId)) return // 外部目录不自动改造结构
        val dir = File(root, bookId)
        if (!dir.isDirectory) return
        STANDARD_FOLDERS.forEach { File(dir, it).mkdirs() }
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in TEXT_EXTENSIONS }
            .forEach { file ->
                val target = File(File(dir, "章节内容"), file.name)
                if (!target.exists()) file.renameTo(target)
            }
        dir.setLastModified(System.currentTimeMillis())
    }

    fun deleteBook(bookId: String) {
        if (isMountedBook(bookId)) {
            val (mountId, name) = parseMountBookId(bookId) ?: return
            val mount = settingsRepository.mountedFolders.value.firstOrNull { it.id == mountId }
                ?: return
            val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }
                .getOrNull() ?: return
            runCatching { tree.findFile(name)?.delete() }
        } else {
            File(root, bookId).deleteRecursively()
        }
        reload()
    }

    /** 列出书稿内某个相对路径下的目录与文件；目录在前，按名称排序。 */
    fun listEntries(bookId: String, relativePath: String): List<NovelEntry> {
        if (isMountedBook(bookId)) {
            return listMountedEntries(bookId, relativePath)
        }
        ensureStructure(bookId)
        val dir = resolveLocal(bookId, relativePath) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .map {
                NovelEntry(
                    name = it.name,
                    path = if (relativePath.isBlank()) it.name else "$relativePath/${it.name}",
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    updatedAt = it.lastModified(),
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

    /**
     * 导入 txt / md / docx：自动识别书名与章节，写入「章节内容」。
     * 章节名写入文件名，未识别到章节时整篇作为一章。
     */
    fun importBook(fileName: String, bytes: ByteArray): Result<Novel> = runCatching {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_IMPORT_EXTENSIONS) {
            "仅支持 TXT / DOCX / MD 格式"
        }
        val text = when (extension) {
            "docx" -> extractDocx(bytes)
            else -> decodeText(bytes)
        }.trim()
        require(text.isNotBlank()) { "文件内容为空" }

        val title = detectTitle(text, fileName)
        val chapters = splitChapters(text)
        val novel = createBook(title, coverIndex = 0)
        val chapterDir = File(File(root, novel.id), "章节内容")
        chapterDir.mkdirs()
        chapters.forEachIndexed { index, chapter ->
            val name = "%03d %s.md".format(index + 1, safeFileName(chapter.title))
            File(chapterDir, name).writeText(chapter.body)
        }
        if (chapters.isEmpty()) {
            File(chapterDir, "全文.md").writeText(text)
        }
        reload()
        _books.value.firstOrNull { it.id == novel.id } ?: novel
    }

    // ── 内部实现 ────────────────────────────────────────────────

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
        val files = if (chapterDir.isDirectory) {
            chapterDir.listFiles().orEmpty()
        } else {
            dir.listFiles().orEmpty()
        }
        return files.count { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
    }

    private fun countMountedChapters(dir: DocumentFile): Int {
        val chapterDir = dir.findFile("章节内容")?.takeIf { it.isDirectory } ?: return 0
        return runCatching { chapterDir.listFiles() }
            .getOrNull()
            .orEmpty()
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

    private fun writeMeta(dir: File, title: String, coverIndex: Int) {
        runCatching {
            File(dir, META_FILE).writeText(
                JSONObject()
                    .put("title", title)
                    .put("cover", coverIndex)
                    .toString(),
            )
        }
    }

    private fun detectTitle(content: String, fileName: String): String {
        Regex("《([^》\\n]{1,40})》").find(content)?.let { return it.groupValues[1].trim() }
        content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("#")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val base = fileName.substringBeforeLast('.').trim()
        return base.ifBlank { "未命名作品" }
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

    /** docx 本质是 zip，直接抽 word/document.xml 里的段落文本，无需额外依赖。 */
    private fun extractDocx(bytes: ByteArray): String {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var found: String? = null
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    found = zip.readBytes().toString(Charsets.UTF_8)
                    break
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
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // ── 挂载目录（SAF）支持 ──────────────────────────────────────

    private fun isMountedBook(bookId: String): Boolean = bookId.startsWith(MOUNT_PREFIX)

    private fun mountBookId(mountId: String, dirName: String): String =
        "$MOUNT_PREFIX$mountId:$dirName"

    private fun parseMountBookId(bookId: String): Pair<String, String>? {
        val rest = bookId.removePrefix(MOUNT_PREFIX)
        val idx = rest.indexOf(':')
        if (idx <= 0) return null
        return rest.substring(0, idx) to rest.substring(idx + 1)
    }

    private fun mountedRoot(bookId: String): DocumentFile? {
        val (mountId, dirName) = parseMountBookId(bookId) ?: return null
        val mount = settingsRepository.mountedFolders.value.firstOrNull { it.id == mountId }
            ?: return null
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }
            .getOrNull() ?: return null
        return runCatching { tree.findFile(dirName) }.getOrNull()?.takeIf { it.isDirectory }
    }

    private fun resolveMounted(bookId: String, relativePath: String): DocumentFile? {
        var current = mountedRoot(bookId) ?: return null
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        for (seg in segments) {
            current = current.findFile(seg) ?: return null
        }
        return current
    }

    private fun listMountedEntries(bookId: String, relativePath: String): List<NovelEntry> {
        val dir = if (relativePath.isBlank()) {
            mountedRoot(bookId)
        } else {
            resolveMounted(bookId, relativePath)
        } ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return runCatching { dir.listFiles() }
            .getOrNull()
            .orEmpty()
            .filter { !it.name.orEmpty().startsWith(".") }
            .map {
                val name = it.name ?: return@map null
                NovelEntry(
                    name = name,
                    path = if (relativePath.isBlank()) name else "$relativePath/$name",
                    isDirectory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    updatedAt = it.lastModified(),
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
            current = current.findFile(seg)
                ?: current.createDirectory(seg)
                ?: return false
        }
        val fileName = segments.last()
        val existing = current.findFile(fileName)
        val target = if (existing != null && existing.isFile) {
            existing
        } else {
            current.createFile("text/plain", fileName) ?: return false
        }
        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    private fun safeTitle(raw: String): String =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "未命名作品" }

    private fun safeFileName(raw: String): String =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").take(60).ifBlank { "未命名章节" }

    private companion object {
        const val META_FILE = ".mozhuan.json"
        const val MOUNT_PREFIX = "mount:"
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
