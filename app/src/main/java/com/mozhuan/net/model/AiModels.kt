package com.mozhuan.net.model

/**
 * 统一的 AI 请求入参实体。
 * 上层业务只构造这个模型，不感知后端差异。
 */
data class AiRequest(
    /** 用户输入的提示词 / 指令。 */
    val prompt: String,
    /** 系统提示词，用于设定 AI 角色。 */
    val systemPrompt: String = "",
    /** 模型名称，留空则由后端使用默认模型。 */
    val model: String = "",
    /** 温度参数，控制输出随机性。 */
    val temperature: Double = 0.7,
    /** 最大输出 token 数，0 表示不限制。 */
    val maxTokens: Int = 0,
)

/**
 * 统一的 SSE 流式返回块。
 * 每个适配器将后端推送的原始数据解析为这个模型。
 */
data class AiChunk(
    /** 本次推送的文本增量。 */
    val delta: String,
    /** 是否为最后一个块（流结束标记）。 */
    val isDone: Boolean = false,
    /** 后端返回的原始 finish_reason，调试用。 */
    val finishReason: String? = null,
)

/**
 * 统一的 AI 调用结果。
 * 用于非流式场景或流式结束后的汇总。
 */
data class AiResult(
    /** 完整生成文本。 */
    val text: String,
    /** 是否成功。 */
    val success: Boolean,
    /** 错误信息，成功时为 null。 */
    val error: String? = null,
)

/**
 * 后端配置实体。
 * 每个后端（OpenMinis / OpenWrite）各持有一份。
 */
data class BackendConfig(
    /** 后端基础地址，如 https://api.openminis.com */
    val baseUrl: String,
    /** API 密钥。 */
    val apiKey: String,
    /** 默认模型名称。 */
    val defaultModel: String = "",
)

/**
 * SSE 流式回调接口。
 * 上层通过实现这个接口接收流式数据。
 */
interface AiStreamCallback {
    /** 收到新的文本增量。 */
    fun onChunk(chunk: AiChunk)

    /** 流正常结束。 */
    fun onComplete()

    /** 发生错误。 */
    fun onError(error: String)
}
