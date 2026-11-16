package com.mozhuan.net

import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
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

/** 统一的 AI 网络层，仅调用 OpenAI 兼容 chat/completions 接口。 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 发起 SSE 流式对话，返回底层 [Call] 供上层取消生成。 */
    fun streamChat(
        settings: ApiSettings,
        messages: List<AiMessage>,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
        onUsage: (TokenUsage) -> Unit = {},
    ): Call {
        val request = Request.Builder()
            // 用户配置的端点原样发送，不自动拼接任何路径。
            .url(settings.baseUrl.trim())
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(buildPayload(settings, messages, stream = true).toRequestBody(JSON))
            .build()

        val call = http.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) onComplete() else onError(networkError(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                            onError(httpError(resp.code, body))
                            return
                        }
                        runCatching { parseSseStream(resp, onDelta, onUsage) }
                            .onSuccess { onComplete() }
                            .onFailure { error -> onError(networkError(error)) }
                    }
                }
            },
        )
        return call
    }

    /** 非流式请求，用于「测试连接」。 */
    suspend fun chat(settings: ApiSettings, messages: List<AiMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(settings.baseUrl.trim())
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(buildPayload(settings, messages, stream = false).toRequestBody(JSON))
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw HttpException(
                            response.code,
                            response.body?.string().orEmpty(),
                        )
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    Result.success(
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("content", ""),
                    )
                }
            } catch (error: Exception) {
                Result.failure(Exception(networkError(error)))
            }
        }

    private fun buildPayload(
        settings: ApiSettings,
        messages: List<AiMessage>,
        stream: Boolean,
    ): String = JSONObject().apply {
        put("model", settings.model)
        put("stream", stream)
        put("temperature", settings.temperature.toDouble())
        put("top_p", settings.topP.toDouble())
        if (settings.maxTokens > 0) put("max_tokens", settings.maxTokens)
        if (stream) {
            put("stream_options", JSONObject().put("include_usage", true))
        }
        put(
            "messages",
            JSONArray().apply {
                messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.content),
                    )
                }
            },
        )
    }.toString()

    /** 解析 SSE 增量与最终 usage 数据。 */
    private fun parseSseStream(
        response: Response,
        onDelta: (String) -> Unit,
        onUsage: (TokenUsage) -> Unit,
    ) {
        val source = response.body?.source() ?: error("响应体为空")
        source.use { src ->
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") return

                runCatching {
                    val item = JSONObject(data)
                    item.optJSONObject("usage")?.let { usage ->
                        onUsage(
                            TokenUsage(
                                inputTokens = usage.optInt(
                                    "prompt_tokens",
                                    usage.optInt("input_tokens", 0),
                                ),
                                outputTokens = usage.optInt(
                                    "completion_tokens",
                                    usage.optInt("output_tokens", 0),
                                ),
                            ),
                        )
                    }

                    val choices = item.optJSONArray("choices") ?: return@runCatching
                    if (choices.length() == 0) return@runCatching
                    val delta = choices.getJSONObject(0)
                        .optJSONObject("delta")
                        ?.optString("content", "")
                        .orEmpty()
                    if (delta.isNotEmpty()) onDelta(delta)
                }
            }
        }
    }

    private fun httpError(code: Int, body: String): String = when (code) {
        401 -> "鉴权失败（401）：请检查 API Key 是否正确或已失效"
        404 -> "接口不存在（404）：请确认端点完整指向 /chat/completions"
        408 -> "请求超时（408）：服务器未及时响应，请稍后重试"
        429 -> "请求过于频繁（429）：请稍后重试或检查服务商限额"
        else -> "请求失败（HTTP $code）：${body.take(180).ifBlank { "服务器未返回详细信息" }}"
    }

    private fun networkError(error: Throwable): String = when (error) {
        is HttpException -> httpError(error.code, error.body)
        is SocketTimeoutException -> "连接超时：请检查网络、端点地址或稍后重试"
        is UnknownHostException -> "无法解析服务器地址：请检查端点域名是否正确"
        is SSLException -> "安全连接失败：请检查端点的 HTTPS 配置"
        is IllegalArgumentException -> "端点格式不正确：请输入完整的 http:// 或 https:// 地址"
        else -> "请求失败：${error.message?.takeIf { it.isNotBlank() } ?: "请检查网络连接"}"
    }

    private class HttpException(
        val code: Int,
        val body: String,
    ) : Exception("HTTP $code")

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
