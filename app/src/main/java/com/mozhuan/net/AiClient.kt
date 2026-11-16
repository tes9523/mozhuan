package com.mozhuan.net

import com.mozhuan.data.model.ApiSettings
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
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 统一的 AI 网络层：单一 OpenAI 兼容 SSE 适配器。
 *
 * 所有 AI 能力共用同一套 [ApiSettings]（地址 / Key / 模型 / 参数），
 * 通过 System Prompt（人格）与对话上下文区分任务。
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // 流式响应可能较长，读超时给足余量。
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 发起 SSE 流式对话。
     *
     * @return 底层 [Call]，供上层取消生成
     */
    fun streamChat(
        settings: ApiSettings,
        messages: List<AiMessage>,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ): Call {
        val request = Request.Builder()
            .url(resolveEndpoint(settings.baseUrl))
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(buildPayload(settings, messages, stream = true).toRequestBody(JSON))
            .build()

        val call = http.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) onComplete() else onError(e.message ?: "网络请求失败")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                            onError("HTTP ${resp.code} ${body.take(300)}")
                            return
                        }
                        runCatching { parseSseStream(resp, onDelta) }
                            .onSuccess { onComplete() }
                            .onFailure { error -> onError(error.message ?: "流式解析失败") }
                    }
                }
            },
        )
        return call
    }

    /** 非流式请求，用于「测试连接」。 */
    suspend fun chat(settings: ApiSettings, messages: List<AiMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(resolveEndpoint(settings.baseUrl))
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(buildPayload(settings, messages, stream = false).toRequestBody(JSON))
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} ${response.body?.string().orEmpty().take(300)}")
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "")
                }
            }
        }

    /** 构造 OpenAI 兼容请求体，注入完整参数。 */
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

    /** 解析 SSE：逐行读取 `data:` 行，提取 choices[0].delta.content。 */
    private fun parseSseStream(response: Response, onDelta: (String) -> Unit) {
        val source = response.body?.source() ?: error("响应体为空")
        source.use { src ->
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") return
                val delta = runCatching {
                    val choices = JSONObject(data).optJSONArray("choices") ?: return@runCatching ""
                    if (choices.length() == 0) return@runCatching ""
                    choices.getJSONObject(0)
                        .optJSONObject("delta")
                        ?.optString("content", "")
                        .orEmpty()
                }.getOrDefault("")
                if (delta.isNotEmpty()) onDelta(delta)
            }
        }
    }

    /**
     * 兼容多种接口地址写法：
     * - 已以 /chat/completions 结尾 → 原样使用
     * - 以 /v1 结尾 → 补 /chat/completions
     * - 其他 → 补 /v1/chat/completions
     */
    private fun resolveEndpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
