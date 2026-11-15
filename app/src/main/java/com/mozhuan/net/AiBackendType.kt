package com.mozhuan.net

/**
 * 支持的 AI 后端类型。
 * 新增后端时在此扩展枚举值，并实现对应的 [AiBackend] 适配器。
 */
enum class AiBackendType(val displayName: String) {
    OPEN_MINIS("OpenMinis"),
    OPEN_WRITE("OpenWrite"),
    ;

    companion object {
        fun from(name: String): AiBackendType =
            entries.firstOrNull { it.name == name } ?: OPEN_MINIS
    }
}
