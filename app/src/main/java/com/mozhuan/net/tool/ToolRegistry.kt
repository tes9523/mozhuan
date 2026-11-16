package com.mozhuan.net.tool

import android.content.Context
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.model.ToolSettings
import com.mozhuan.data.repository.MemoryRepository
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
 * 所有文件操作都以应用私有目录为根，且只能访问 book / skills / memory 三个子目录。
 */
class ToolRegistry(
    context: Context,
    private val memoryRepository: MemoryRepository,
    private val proxyProvider: () -> ProxySettings,
) {

    private val root = context.applicationContext.filesDir
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
                    description = "列出书稿、技能或记忆目录下的文件。只读，不会修改任何内容。",
                    parameters = parameters(
                        "path" to stringProperty("目录路径，例如 book、skills、memory"),
                    ),
                ),
            )
            add(
                ToolSpec(
                    name = "read_file",
                    description = "读取书稿、技能或记忆目录中的文本文件，用于查阅设定、参考素材或已保存的记忆。",
                    parameters = parameters(
                        "path" to stringProperty("文件路径，例如 book/第一章.md、skills/xxx/SKILL.md"),
                    ),
                ),
            )
        }
        if (settings.writeFiles) {
            add(
                ToolSpec(
                    name = "write_file",
                    description = "把文本写入书稿目录（book/）。仅用于保存正文、大纲、设定等创作内容，会自动创建父目录。",
                    parameters = parameters(
                        "path" to stringProperty("相对书稿目录的路径，例如 第一章.md"),
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
                    description = "把值得长期记住的用户偏好、作品设定或创作约定写入今天的记忆文件，后续对话会自动携带。",
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
        val file = resolve(path) ?: return "路径不合法：$path"
        if (!file.exists() || !file.isFile) return "文件不存在：$path"
        if (file.length() > MAX_FILE_BYTES) return "文件过大，无法读取（上限 ${MAX_FILE_BYTES / 1024}KB）"
        val text = runCatching { file.readText() }.getOrElse { return "读取失败：${it.message}" }
        return text.take(MAX_TEXT_CHARS)
    }

    private fun writeFile(path: String, content: String): String {
        val safe = path.trim().removePrefix("book/").removePrefix("book\\")
        if (safe.isBlank() || safe.contains("..") || safe.startsWith("/") || safe.contains('\\')) {
            return "路径不合法：$path"
        }
        if (content.length > MAX_TEXT_CHARS) {
            return "内容过长，单次最多写入 $MAX_TEXT_CHARS 字"
        }
        val file = File(bookDir, safe)
        file.parentFile?.mkdirs()
        return runCatching {
            file.writeText(content)
            "已写入 book/$safe（${content.length} 字）"
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
