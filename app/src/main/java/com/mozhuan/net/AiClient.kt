package com.mozhuan.net

import com.mozhuan.data.model.ApiStyle
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** 流式增量：正文、思考过程与 Token 用量。 */
sealed interface StreamChunk {
    data class Text(val text: String) : StreamChunk
    data class Reasoning(val text: String) : StreamChunk
    data class Usage(val inputTokens: Int, val outputTokens: Int) : StreamChunk
    data object Completed : StreamChunk
}

/** 网络层错误，message 已转为可直接展示的中文提示。 */
class AiException(message: String) : Exception(message)

/**
 * AI 网络层，行为对齐 OpenMinis：
 * - 服务商地址 = 基础地址（按需追加 /v1）+ 接口路径；
 * - OpenAI 兼容走 /chat/completions，Anthropic 走 /messages；
 * - SSE 流式解析正文、思考过程与 usage，支持取消；
 * - 模型列表从 /models 拉取。
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        provider: ProviderInstance,
        entry: ModelEntry,
        messages: List<AiMessage>,
        systemPrompt: String? = null,
    ): Flow<StreamChunk> {
        val request = buildChatRequest(provider, entry, messages, systemPrompt, stream = true)
        return flow {
            val call = http.newCall(request)
            val job = currentCoroutineContext()[Job]
            job?.invokeOnCompletion { if (job?.isCancelled == true) call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw AiException(httpError(response.code, response.body?.string().orEmpty()))
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("application/json")) {
                        emitJsonResponse(response, provider.type.apiStyle)
                    } else {
                        emitSse(response, provider.type.apiStyle)
                    }
                }
                emit(StreamChunk.Completed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiException) {
                throw error
            } catch (error: Exception) {
                if (job?.isCancelled == true) throw CancellationException("已停止生成")
                throw AiException(networkError(error))
            }
        }.flowOn(Dispatchers.IO)
    }

    /** 非流式请求，用于「测试连接」。 */
    suspend fun complete(
        provider: ProviderInstance,
        entry: ModelEntry,
        messages: List<AiMessage>,
        systemPrompt: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildChatRequest(provider, entry, messages, systemPrompt, stream = false)
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiException(httpError(response.code, response.body?.string().orEmpty()))
                }
                val body = JSONObject(response.body?.string().orEmpty())
                val text = when (provider.type.apiStyle) {
                    ApiStyle.OPENAI_CHAT -> body.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    ApiStyle.ANTHROPIC_MESSAGES -> body.optJSONArray("content")
                        ?.let { blocks ->
                            buildString {
                                for (index in 0 until blocks.length()) {
                                    val block = blocks.optJSONObject(index) ?: continue
                                    if (block.optString("type") == "text") append(block.optString("text"))
                                }
                            }
                        }
                        .orEmpty()
                }
                Result.success(text)
            }
        } catch (error: Exception) {
            Result.failure(AiException(networkError(error)))
        }
    }

    /** 拉取服务商支持的模型列表。 */
    suspend fun fetchModels(provider: ProviderInstance): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val base = provider.effectiveBaseUrl
            if (base.isBlank()) throw AiException("请先填写请求地址")
            val builder = Request.Builder()
                .url("$base/models")
                .addHeader("Accept", "application/json")
                .get()
            applyAuth(builder, provider)
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiException(httpError(response.code, response.body?.string().orEmpty()))
                }
                val body = JSONObject(response.body?.string().orEmpty())
                val items = body.optJSONArray("data") ?: body.optJSONArray("models")
                val names = LinkedHashSet<String>()
                if (items != null) {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.optString("id", item.optString("name", "")).trim()
                        if (id.isNotEmpty()) names += id
                    }
                }
                if (names.isEmpty()) {
                    Result.failure(AiException("获取模型列表失败：服务商未返回模型，可手动填写模型名"))
                } else {
                    Result.success(names.sorted())
                }
            }
        } catch (error: Exception) {
            Result.failure(AiException(networkError(error)))
        }
    }

    // ── 请求构建 ─────────────────────────────────────────────────

    private fun buildChatRequest(
        provider: ProviderInstance,
        entry: ModelEntry,
        messages: List<AiMessage>,
        systemPrompt: String?,
        stream: Boolean,
    ): Request {
        val base = provider.effectiveBaseUrl
        if (base.isBlank()) throw AiException("请先填写请求地址")
        val path = when (provider.type.apiStyle) {
            ApiStyle.OPENAI_CHAT -> "/chat/completions"
            ApiStyle.ANTHROPIC_MESSAGES -> "/messages"
        }
        val payload = when (provider.type.apiStyle) {
            ApiStyle.OPENAI_CHAT -> openAiPayload(entry, messages, systemPrompt, stream)
            ApiStyle.ANTHROPIC_MESSAGES -> anthropicPayload(entry, messages, systemPrompt, stream)
        }
        val builder = Request.Builder()
            .url("$base$path")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", if (stream) "text/event-stream" else "application/json")
            .post(payload.toString().toRequestBody(JSON))
        applyAuth(builder, provider)
        return builder.build()
    }

    private fun applyAuth(builder: Request.Builder, provider: ProviderInstance) {
        val key = provider.apiKey.trim()
        if (key.isEmpty()) return
        if (provider.type.apiStyle == ApiStyle.ANTHROPIC_MESSAGES) {
            builder.addHeader("x-api-key", key)
            builder.addHeader("anthropic-version", "2023-06-01")
        } else {
            builder.addHeader("Authorization", "Bearer $key")
        }
    }

    private fun openAiPayload(
        entry: ModelEntry,
        messages: List<AiMessage>,
        systemPrompt: String?,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", entry.modelId)
        put("stream", stream)
        val reasoningModel = REASONING_MODEL.containsMatchIn(entry.modelId.lowercase())
        if (!reasoningModel) {
            put("temperature", entry.temperature.toDouble())
            put("top_p", entry.topP.toDouble())
        }
        if (entry.maxTokens > 0) {
            put(
                if (reasoningModel) "max_completion_tokens" else "max_tokens",
                entry.maxTokens,
            )
        }
        if (stream) put("stream_options", JSONObject().put("include_usage", true))
        put(
            "messages",
            JSONArray().apply {
                systemPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { prompt ->
                    put(JSONObject().put("role", "system").put("content", prompt))
                }
                messages.forEach { message ->
                    val content = message.content.trim()
                    if (content.isNotEmpty()) {
                        put(JSONObject().put("role", message.role).put("content", content))
                    }
                }
            },
        )
    }

    private fun anthropicPayload(
        entry: ModelEntry,
        messages: List<AiMessage>,
        systemPrompt: String?,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", entry.modelId)
        put("max_tokens", entry.maxTokens.coerceAtLeast(1))
        put("stream", stream)
        put("temperature", entry.temperature.toDouble())
        put("top_p", entry.topP.toDouble())
        systemPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { put("system", it) }

        val merged = mutableListOf<AiMessage>()
        messages.forEach { message ->
            val content = message.content.trim()
            if (content.isEmpty()) return@forEach
            val last = merged.lastOrNull()
            if (last != null && last.role == message.role) {
                merged[merged.lastIndex] = last.copy(content = last.content + "\n\n" + content)
            } else {
                merged += AiMessage(role = message.role, content = content)
            }
        }
        if (merged.firstOrNull()?.role == "assistant") {
            merged.add(0, AiMessage(role = "user", content = "继续"))
        }
        put(
            "messages",
            JSONArray().apply {
                merged.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            },
        )
    }

    // ── 响应解析 ─────────────────────────────────────────────────

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitJsonResponse(
        response: Response,
        style: ApiStyle,
    ) {
        val body = JSONObject(response.body?.string().orEmpty())
        when (style) {
            ApiStyle.OPENAI_CHAT -> {
                val choice = body.optJSONArray("choices")?.optJSONObject(0)
                val message = choice?.optJSONObject("message")
                val text = message?.optString("content").orEmpty()
                if (text.isNotEmpty()) emit(StreamChunk.Text(text))
                parseOpenAiUsage(body.optJSONObject("usage"))?.let { emit(it) }
            }
            ApiStyle.ANTHROPIC_MESSAGES -> {
                val blocks = body.optJSONArray("content")
                if (blocks != null) {
                    for (index in 0 until blocks.length()) {
                        val block = blocks.optJSONObject(index) ?: continue
                        when (block.optString("type")) {
                            "text" -> block.optString("text").takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamChunk.Text(it)) }
                            "thinking" -> block.optString("thinking").takeIf { it.isNotEmpty() }
                                ?.let { emit(StreamChunk.Reasoning(it)) }
                        }
                    }
                }
                parseAnthropicUsage(body.optJSONObject("usage"))?.let { emit(it) }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitSse(
        response: Response,
        style: ApiStyle,
    ) {
        val source = response.body?.source() ?: throw AiException("响应体为空")
        source.use { src ->
            while (!src.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") return
                when (style) {
                    ApiStyle.OPENAI_CHAT -> emitOpenAiChunk(data)
                    ApiStyle.ANTHROPIC_MESSAGES -> if (emitAnthropicChunk(data)) return
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitOpenAiChunk(data: String) {
        val item = runCatching { JSONObject(data) }.getOrNull() ?: return
        item.optJSONObject("error")?.let { error ->
            throw AiException(error.optString("message", "服务商返回错误"))
        }
        parseOpenAiUsage(item.optJSONObject("usage"))?.let { emit(it) }

        val choices = item.optJSONArray("choices") ?: return
        if (choices.length() == 0) return
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return
        delta.optString("reasoning_content").takeIf { it.isNotEmpty() }
            ?.let { emit(StreamChunk.Reasoning(it)) }
        delta.optString("reasoning").takeIf { it.isNotEmpty() }
            ?.let { emit(StreamChunk.Reasoning(it)) }
        delta.optString("content").takeIf { it.isNotEmpty() }
            ?.let { emit(StreamChunk.Text(it)) }
    }

    /** 返回 true 表示收到 message_stop。 */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitAnthropicChunk(data: String): Boolean {
        val item = runCatching { JSONObject(data) }.getOrNull() ?: return false
        when (item.optString("type")) {
            "message_start" -> {
                val usage = item.optJSONObject("message")?.optJSONObject("usage")
                usage?.optInt("input_tokens", 0)?.takeIf { it > 0 }
                    ?.let { emit(StreamChunk.Usage(inputTokens = it, outputTokens = 0)) }
            }
            "content_block_delta" -> {
                val delta = item.optJSONObject("delta") ?: return false
                when (delta.optString("type")) {
                    "text_delta" -> delta.optString("text").takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamChunk.Text(it)) }
                    "thinking_delta" -> delta.optString("thinking").takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamChunk.Reasoning(it)) }
                }
            }
            "message_delta" -> {
                val usage = item.optJSONObject("usage")
                usage?.optInt("output_tokens", 0)?.takeIf { it > 0 }
                    ?.let { emit(StreamChunk.Usage(inputTokens = 0, outputTokens = it)) }
            }
            "error" -> {
                val message = item.optJSONObject("error")?.optString("message").orEmpty()
                throw AiException(message.ifBlank { "服务商返回错误" })
            }
            "message_stop" -> return true
        }
        return false
    }

    private fun parseOpenAiUsage(usage: JSONObject?): StreamChunk.Usage? {
        if (usage == null) return null
        val input = usage.optInt("prompt_tokens", usage.optInt("input_tokens", 0))
        val output = usage.optInt("completion_tokens", usage.optInt("output_tokens", 0))
        if (input <= 0 && output <= 0) return null
        return StreamChunk.Usage(input, output)
    }

    private fun parseAnthropicUsage(usage: JSONObject?): StreamChunk.Usage? {
        if (usage == null) return null
        val input = usage.optInt("input_tokens", 0)
        val output = usage.optInt("output_tokens", 0)
        if (input <= 0 && output <= 0) return null
        return StreamChunk.Usage(input, output)
    }

    // ── 错误映射 ─────────────────────────────────────────────────

    private fun httpError(code: Int, body: String): String {
        val detail = extractError(body)
        return when (code) {
            400 -> "请求参数被拒绝（400）：${detail.ifBlank { "请检查模型名称与生成参数" }}"
            401, 403 -> "鉴权失败（$code）：API Key 无效或无权访问该模型"
            404 -> "接口不存在（404）：请检查服务商类型、请求地址与 /v1 后缀设置"
            408 -> "请求超时（408）：服务商未及时响应，请稍后重试"
            429 -> "请求过于频繁（429）：请稍后重试或检查服务商限额"
            in 500..599 -> "服务商错误（$code）：${detail.ifBlank { "请稍后重试" }}"
            else -> "请求失败（HTTP $code）：${detail.ifBlank { "服务商未返回详细信息" }}"
        }
    }

    private fun extractError(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        runCatching {
            val json = JSONObject(trimmed)
            val error = json.optJSONObject("error")
            val message = error?.optString("message").orEmpty()
            if (message.isNotBlank()) return message.take(200)
            val direct = json.optString("message").orEmpty()
            if (direct.isNotBlank()) return direct.take(200)
        }
        return trimmed.take(200)
    }

    private fun networkError(error: Throwable): String = when (error) {
        is AiException -> error.message.orEmpty()
        is SocketTimeoutException -> "连接超时：请检查网络、请求地址或稍后重试"
        is UnknownHostException -> "无法解析服务器地址：请检查请求地址的域名是否正确"
        is SSLException -> "安全连接失败：请检查请求地址的 HTTPS 配置"
        is IllegalArgumentException -> "请求地址格式不正确：请填写完整的 http:// 或 https:// 地址"
        is IOException -> "网络请求失败：${error.message?.takeIf { it.isNotBlank() } ?: "请检查网络连接"}"
        else -> "请求失败：${error.message?.takeIf { it.isNotBlank() } ?: "请检查网络连接"}"
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val REASONING_MODEL = Regex("^(o1|o3|o4|gpt-5)")
    }
}
