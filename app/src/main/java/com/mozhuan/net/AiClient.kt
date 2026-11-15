package com.mozhuan.net

import com.mozhuan.net.model.AiRequest
import com.mozhuan.net.model.AiResult
import com.mozhuan.net.model.AiStreamCallback
import com.mozhuan.net.model.BackendConfig
import com.mozhuan.net.openminis.OpenMinisAdapter
import com.mozhuan.net.openwrite.OpenWriteAdapter

/**
 * AI 网络层统一入口。
 *
 * 上层业务只持有 [AiClient]，通过 [streamChat] / [chat] 发起请求，
 * 无需关心当前使用的是哪个后端。
 *
 * 新增后端时：
 * 1. 在 [AiBackendType] 中扩展枚举
 * 2. 实现 [AiBackend] 接口
 * 3. 在 [adapters] 中注册
 */
class AiClient {

    /** 已注册的后端适配器。 */
    private val adapters: Map<AiBackendType, AiBackend> = mapOf(
        AiBackendType.OPEN_MINIS to OpenMinisAdapter(),
        AiBackendType.OPEN_WRITE to OpenWriteAdapter(),
    )

    /** 当前生效的后端类型，由设置页切换。 */
    var currentBackend: AiBackendType = AiBackendType.OPEN_MINIS

    /**
     * 发起 SSE 流式请求。
     *
     * @param config 当前后端的配置（地址、密钥）
     * @param request 统一入参
     * @param callback 流式回调
     */
    fun streamChat(config: BackendConfig, request: AiRequest, callback: AiStreamCallback) {
        val adapter = adapters[currentBackend]
            ?: throw IllegalStateException("未注册的后端：$currentBackend")
        adapter.streamChat(config, request, callback)
    }

    /**
     * 发起非流式请求。
     *
     * @param config 当前后端的配置
     * @param request 统一入参
     * @return 统一结果模型
     */
    suspend fun chat(config: BackendConfig, request: AiRequest): AiResult {
        val adapter = adapters[currentBackend]
            ?: throw IllegalStateException("未注册的后端：$currentBackend")
        return adapter.chat(config, request)
    }
}
