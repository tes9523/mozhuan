package com.mozhuan.net

import com.mozhuan.data.model.AiConfig
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
 * 统一的 AI 网络层：只保留一个 OpenAI 兼容的 SSE 适配器。
 *
 * 所有 AI 能力（全局对话、绑定书稿对话、章节续写）共用同一套 API 配置，
 * 通过上层传入不同的技能 System Prompt 来区分任务。
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
     * @param config API 配置（地址、密钥、模型）
     * @param messages 完整消息列表（含 system 消息与多轮历史）
     * @param onDelta 文本增量回调
     * @param onComplete 正常结束回调
     * @param onError 错误回调
     * @return 底层 [Call]，供上层取消生成
     */
    fun streamChat(
        config: AiConfig,
        messages: List<AiMessage>,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ): Call {
        val payload = JSONObject().apply {
            put("model", config.model.ifBlank { "gpt-3.5-turbo" })
            put("stream", true)
            put("temperature", 0.8)
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
        }

        val request = Request.Builder()
            .url(resolveEndpoint(config.baseUrl))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val call = http.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // 主动取消时不视为错误，由上层自行判断。
                    if (call.isCanceled()) {
                        onComplete()
                    } else {
                        onError(e.message ?: "网络请求失败")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val body = runCatching { it.body?.string() }.getOrNull().orEmpty()
                            onError("HTTP ${it.code} ${body.take(300)}")
                            return
                        }
                        runCatching { parseSseStream(it, onDelta) }
                            .onSuccess { onComplete() }
                            .onFailure { error -> onError(error.message ?: "流式解析失败") }
                    }
                }
            },
        )
        return call
    }

    /**
     * 非流式请求，一次返回完整文本。主要用于服务连通性测试。
     */
    suspend fun chat(config: AiConfig, messages: List<AiMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("model", config.model.ifBlank { "gpt-3.5-turbo" })
                    put("stream", false)
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
                }
                val request = Request.Builder()
                    .url(resolveEndpoint(config.baseUrl))
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(JSON))
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
