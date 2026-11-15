package com.mozhuan.net.openwrite

import com.mozhuan.net.AiBackend
import com.mozhuan.net.AiBackendType
import com.mozhuan.net.model.AiChunk
import com.mozhuan.net.model.AiRequest
import com.mozhuan.net.model.AiResult
import com.mozhuan.net.model.AiStreamCallback
import com.mozhuan.net.model.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Callback
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenWrite 后端 SSE 适配器。
 *
 * 协议：OpenWrite 自定义格式
 * - POST {baseUrl}/api/generate
 * - 请求体：{prompt, system, model, temperature, max_tokens, stream}
 * - SSE 响应：data: {text: "...", done: false}
 */
class OpenWriteAdapter : AiBackend {

    override val type: AiBackendType = AiBackendType.OPEN_WRITE

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun streamChat(config: BackendConfig, request: AiRequest, callback: AiStreamCallback) {
        val body = buildRequestBody(request, stream = true)
        val httpRequest = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/api/generate")
            .addHeader("X-API-Key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("网络请求失败：${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onError("HTTP ${response.code}：${response.body?.string()}")
                    return
                }
                try {
                    parseSseStream(response, callback)
                } catch (e: Exception) {
                    callback.onError("SSE 解析异常：${e.message}")
                }
            }
        })
    }

    override suspend fun chat(config: BackendConfig, request: AiRequest): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val body = buildRequestBody(request, stream = false)
                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.trimEnd('/')}/api/generate")
                    .addHeader("X-API-Key", config.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    return@withContext AiResult(
                        text = "",
                        success = false,
                        error = "HTTP ${response.code}：${response.body?.string()}",
                    )
                }
                val json = JSONObject(response.body?.string() ?: "")
                val text = json.optString("text", "")
                AiResult(text = text, success = true)
            } catch (e: Exception) {
                AiResult(text = "", success = false, error = e.message)
            }
        }

    /** 构造 OpenWrite 自定义请求体。 */
    private fun buildRequestBody(request: AiRequest, stream: Boolean): String {
        return JSONObject().apply {
            put("prompt", request.prompt)
            if (request.systemPrompt.isNotBlank()) put("system", request.systemPrompt)
            put("model", request.model.ifBlank { "default" })
            put("temperature", request.temperature)
            if (request.maxTokens > 0) put("max_tokens", request.maxTokens)
            put("stream", stream)
        }.toString()
    }

    /** 逐行解析 OpenWrite SSE 流。 */
    private fun parseSseStream(response: Response, callback: AiStreamCallback) {
        val source = response.body?.source() ?: run {
            callback.onError("响应体为空")
            return
        }
        source.use {
            while (!it.exhausted()) {
                val line = it.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        callback.onChunk(AiChunk(delta = "", isDone = true, finishReason = "stop"))
                        callback.onComplete()
                        return
                    }
                    try {
                        val json = JSONObject(data)
                        val text = json.optString("text", "")
                        val done = json.optBoolean("done", false)
                        if (text.isNotEmpty() || done) {
                            callback.onChunk(AiChunk(delta = text, isDone = done, finishReason = if (done) "stop" else null))
                        }
                        if (done) {
                            callback.onComplete()
                            return
                        }
                    } catch (_: Exception) {
                        // 跳过无法解析的行
                    }
                }
            }
        }
        callback.onComplete()
    }
}
