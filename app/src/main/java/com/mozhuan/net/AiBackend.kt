package com.mozhuan.net

import com.mozhuan.net.model.AiRequest
import com.mozhuan.net.model.AiResult
import com.mozhuan.net.model.AiStreamCallback
import com.mozhuan.net.model.BackendConfig

/**
 * AI 后端统一抽象接口。
 *
 * 每个后端（OpenMinis / OpenWrite）实现此接口，
 * 将各自的 API 协议转换为统一的入参 / 返回模型。
 *
 * 上层业务只面向此接口编程，新增后端无需修改上层代码。
 */
interface AiBackend {

    /** 后端类型标识。 */
    val type: AiBackendType

    /**
     * 发起 SSE 流式请求。
     *
     * @param config 后端配置（地址、密钥）
     * @param request 统一入参
     * @param callback 流式回调
     */
    fun streamChat(config: BackendConfig, request: AiRequest, callback: AiStreamCallback)

    /**
     * 发起非流式请求（一次性返回完整结果）。
     *
     * @param config 后端配置
     * @param request 统一入参
     * @return 统一结果模型
     */
    suspend fun chat(config: BackendConfig, request: AiRequest): AiResult
}
