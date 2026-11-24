package com.mozhuan.net.tool

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.ToolSettings
import com.mozhuan.data.repository.MemoryRepository
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.net.InetSocketAddress
import java.net.Proxy

/** 发给模型的工具定义（OpenAI function calling）。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters),
        )
}

/**
 * 内置工具：书稿读写、目录查看、网页抓取与记忆写入。
 * 文件操作可访问应用私有目录（book / skills / memory）以及挂载的外部文件夹（mounts/<名称>/）。
 */
class ToolRegistry(
    context: Context,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val proxyProvider: () -> ProxySettings,
) {

    private val context = context.applicationContext
    private val root = context.filesDir
    private val bookDir = File(root, "book").apply { mkdirs() }

    @Volatile
    private var cachedProxy: ProxySettings? = null

    @Volatile
    private var cachedHttp: OkHttpClient? = null

    fun specs(settings: ToolSettings): List<ToolSpec> = buildList {
        if (settings.readFiles) {
            add(
                ToolSpec(
                    name = "list_files",
                    description = "列出 book（内置书稿文件夹）/ skills / memory（长期记忆）或挂载的外部文件夹（mounts/<名称>/）下的文件。只读，不会修改任何内容。",
                    parameters = parameters(
                        "path" to stringProperty("目录路径，例如 book、skills、memory、mounts/墨撰"),
                    ),
                ),
            )
            add(
                ToolSpec(
                    name = "read_file",
                    description = "读取书稿、技能、记忆或挂载外部文件夹中的文本文件。任务匹配某个已启用技能时，用此工具按 path 加载其 SKILL.md；也可查阅设定、素材或记忆。",
                    parameters = parameters(
                        "path" to stringProperty("文件路径，例如 book/第一章.md、skills/xxx/SKILL.md、mounts/墨撰/我的小说/大纲.md"),
                    ),
                ),
            )
        }
        if (settings.writeFiles) {
            add(
                ToolSpec(
                    name = "write_file",
                    description = "把文本写入内置书稿文件夹 book/、skills/ 或挂载的外部文件夹 mounts/<名称>/，自动创建父目录。小说正文与设定写 book/<书名>/（设定写 book/<书名>/小说资料/设定/），不要写进 memory（记忆只存偏好）。创建或修改技能时使用 skills/<name>/SKILL.md；新建小说时写入 book/<书名>/... 或 mounts/<挂载名>/<书名>/...；内置 skill-creator 只读，不可写入。",
                    parameters = parameters(
                        "path" to stringProperty("相对路径，例如 第一章.md（书稿）、skills/my-skill/SKILL.md（技能）、mounts/墨撰/新书/章节内容/第01章.md"),
                        "content" to stringProperty("要写入的完整文本"),
                        required = listOf("path", "content"),
                    ),
                ),
            )
        }
        if (settings.webFetch) {
            add(
                ToolSpec(
                    name = "fetch_url",
                    description = "读取一个网页或接口的纯文本内容，用于查资料、核对事实。仅支持 http/https。",
                    parameters = parameters(
                        "url" to stringProperty("完整网址，必须以 http:// 或 https:// 开头"),
                    ),
                ),
            )
        }
        if (settings.memoryWrite) {
            add(
                ToolSpec(
                    name = "save_memory",
                    description = "把值得长期记住的用户偏好或创作约定写入今天的记忆文件，后续对话会自动携带。注意：记忆不是小说设定库，小说设定、世界观、人物等请用 write_file 写入 book/<书名>/小说资料/。",
                    parameters = parameters(
                        "content" to stringProperty("要记住的内容，一句话或一小段"),
                    ),
                ),
            )
        }
    }

    suspend fun execute(name: String, arguments: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val args = runCatching { JSONObject(arguments.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            when (name) {
                "list_files" -> listFiles(args.optString("path").ifBlank { "book" })
                "read_file" -> readFile(args.optString("path"))
                "write_file" -> writeFile(args.optString("path"), args.optString("content"))
                "fetch_url" -> fetchUrl(args.optString("url"))
                "save_memory" -> memoryRepository.appendToToday(args.optString("content"))
                else -> "未知工具：$name"
            }
        }.getOrElse { error -> "工具执行失败：${error.message ?: "未知错误"}" }
    }

    private fun listFiles(path: String): String {
        if (path.startsWith("mounts/")) return listMountedFiles(path)
        val dir = resolve(path) ?: return "路径不合法：$path"
        if (!dir.exists()) return "目录不存在：$path"
        if (!dir.isDirectory) return "不是目录：$path"
        val items = dir.listFiles().orEmpty().sortedBy { it.name }
        if (items.isEmpty()) return "目录为空：$path"
        return items.joinToString("\n") { item ->
            val type = if (item.isDirectory) "[目录]" else "[文件]"
            val size = if (item.isFile) " ${item.length()}B" else ""
            "$type ${relative(item)}$size"
        }
    }

    private fun readFile(path: String): String {
        if (path.startsWith("mounts/")) return readMountedFile(path)
        val file = resolve(path) ?: return "路径不合法：$path"
        if (!file.exists() || !file.isFile) return "文件不存在：$path"
        if (file.length() > MAX_FILE_BYTES) return "文件过大，无法读取（上限 ${MAX_FILE_BYTES / 1024}KB）"
        val text = runCatching { file.readText() }.getOrElse { return "读取失败：${it.message}" }
        return text.take(MAX_TEXT_CHARS)
    }

    private fun writeFile(path: String, content: String): String {
        val normalized = path.trim().replace('\\', '/').removePrefix("/")
        if (normalized.isBlank() || normalized.contains("..") || normalized.contains('\\')) {
            return "路径不合法：$path"
        }
        if (normalized.startsWith("mounts/")) return writeMountedFile(normalized, content)
        val target = when {
            normalized.startsWith("book/") -> normalized
            normalized.startsWith("skills/") -> normalized
            else -> return "只能写入 book/、skills/ 或 mounts/ 目录：$path"
        }
        if (target.startsWith("skills/") &&
            target.removePrefix("skills/").substringBefore('/') == Skill.BUILT_IN_SKILL_CREATOR_ID
        ) {
            return "内置技能 skill-creator 只读，不能修改"
        }
        if (content.length > MAX_TEXT_CHARS) {
            return "内容过长，单次最多写入 $MAX_TEXT_CHARS 字"
        }
        val file = File(root, target)
        file.parentFile?.mkdirs()
        return runCatching {
            file.writeText(content)
            "已写入 $target（${content.length} 字）"
        }.getOrElse { "写入失败：${it.message}" }
    }

    private fun fetchUrl(url: String): String {
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return "仅支持 http/https 网址"
        }
        val request = Request.Builder()
            .url(target)
            .addHeader("User-Agent", "Mozhuan/0.9.2")
            .get()
            .build()
        http().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "请求失败：HTTP ${response.code}"
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_FETCH_CHARS * 4) {
                // 超长内容先截断再处理，避免正则开销过大。
                return clean(body.take(MAX_FETCH_CHARS * 4), contentType).take(MAX_FETCH_CHARS)
            }
            return clean(body, contentType).take(MAX_FETCH_CHARS)
        }
    }

    /** 去掉脚本、样式与标签，保留可读文本。 */
    private fun clean(body: String, contentType: String): String {
        if (!contentType.contains("html")) return body.trim()
        return body
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun resolve(path: String): File? {
        val safe = path.trim().trimStart('/')
        if (safe.isEmpty() || safe.contains("..")) return null
        val allowed = safe == "book" || safe == "skills" || safe == "memory" ||
            safe.startsWith("book/") || safe.startsWith("skills/") || safe.startsWith("memory/")
        if (!allowed) return null
        return File(root, safe)
    }

    private fun relative(file: File): String =
        runCatching { file.relativeTo(root).path }.getOrDefault(file.name)

    // ── 挂载的外部文件夹（SAF） ──────────────────────────────────

    /** 把 mounts/<名称>/<相对路径> 解析到挂载目录下的 DocumentFile。 */
    private fun resolveMounted(path: String): DocumentFile? {
        val rest = path.trim().removePrefix("mounts/").trim('/')
        val segments = rest.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        val mountName = segments.first()
        val mount = settingsRepository.mountedFolders.value
            .firstOrNull { it.name == mountName }
            ?: return null
        var current = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }
            .getOrNull() ?: return null
        for (seg in segments.drop(1)) {
            current = current.findFile(seg) ?: return null
        }
        return current
    }

    private fun listMountedFiles(path: String): String {
        val dir = resolveMounted(path) ?: return "路径不合法或未找到挂载目录：$path"
        if (!dir.isDirectory) return "不是目录：$path"
        val items = runCatching { dir.listFiles().sortedBy { it.name.orEmpty() } }
            .getOrElse { return "读取目录失败：${it.message}" }
        if (items.isEmpty()) return "目录为空：$path"
        return items.joinToString("\n") { item ->
            val type = if (item.isDirectory) "[目录]" else "[文件]"
            val size = if (item.isFile) " ${item.length()}B" else ""
            "$type ${item.name.orEmpty()}$size"
        }
    }

    private fun readMountedFile(path: String): String {
        val file = resolveMounted(path) ?: return "路径不合法或未找到挂载目录：$path"
        if (!file.isFile) return "文件不存在：$path"
        if (file.length() > MAX_FILE_BYTES) return "文件过大，无法读取（上限 ${MAX_FILE_BYTES / 1024}KB）"
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrElse { return "读取失败：${it.message}" }
        return text?.take(MAX_TEXT_CHARS) ?: "读取失败"
    }

    private fun writeMountedFile(path: String, content: String): String {
        val rest = path.trim().removePrefix("mounts/").trim('/')
        val segments = rest.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return "挂载路径格式为 mounts/<名称>/<相对路径>：$path"
        val mountName = segments.first()
        val mount = settingsRepository.mountedFolders.value
            .firstOrNull { it.name == mountName }
            ?: return "未找到挂载的文件夹「$mountName」，请先在设置中挂载"
        var current = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.uri)) }
            .getOrNull() ?: return "挂载目录授权已失效：$mountName"
        for (seg in segments.drop(1).dropLast(1)) {
            current = current.findFile(seg) ?: current.createDirectory(seg) ?: return "创建目录失败：$seg"
        }
        val fileName = segments.last()
        val existing = current.findFile(fileName)
        val target = if (existing != null && existing.isFile) {
            existing
        } else {
            current.createFile("text/plain", fileName) ?: return "创建文件失败：$fileName"
        }
        if (content.length > MAX_TEXT_CHARS) {
            return "内容过长，单次最多写入 $MAX_TEXT_CHARS 字"
        }
        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            "已写入 $path（${content.length} 字）"
        }.getOrElse { "写入失败：${it.message}" }
    }

    private fun http(): OkHttpClient {
        val settings = proxyProvider()
        cachedHttp?.let { if (cachedProxy == settings) return it }
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
        if (settings.enabled && settings.host.isNotBlank() && settings.port in 1..65535) {
            val type = if (settings.type == ProxySettings.PROXY_SOCKS) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            builder.proxy(Proxy(type, InetSocketAddress(settings.host.trim(), settings.port)))
        }
        return builder.build().also {
            cachedProxy = settings
            cachedHttp = it
        }
    }

    private fun parameters(
        vararg properties: Pair<String, JSONObject>,
        required: List<String> = properties.map { it.first },
    ): JSONObject = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply { properties.forEach { (key, value) -> put(key, value) } },
        )
        put("required", JSONArray(required))
    }

    private fun stringProperty(description: String): JSONObject =
        JSONObject().put("type", "string").put("description", description)

    private companion object {
        const val MAX_FILE_BYTES = 200 * 1024L
        const val MAX_TEXT_CHARS = 100_000
        const val MAX_FETCH_CHARS = 20_000
    }
}
