package com.mobilebot.data.settings

object LlmEndpointDefaults {
    /** Gemini via [OpenAI-compatible HTTP API](https://ai.google.dev/gemini-api/docs/openai). */
    const val GEMINI_OPENAI_COMPAT_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"

    /** 智谱 GLM OpenAI-compatible API ([docs](https://open.bigmodel.cn/dev/api#sdk)). */
    const val ZHIPU_OPENAI_COMPAT_BASE = "https://open.bigmodel.cn/api/paas/v4"

    const val OPENAI_DEFAULT_BASE = "https://api.openai.com/v1"

    /** OpenAI-compatible gateway (调试用). */
    const val BITEXING_OPENAI_COMPAT_BASE = "https://bitexingai.com/v1"

    /** MiniMax OpenAI-compatible API ([docs](https://www.minimaxi.com/)). */
    const val MINIMAX_OPENAI_COMPAT_BASE = "https://api.minimaxi.com/v1"

    const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

    /** Bitexing 上常用 Gemini 模型 id（以控制台为准） */
    const val BITEXING_GEMINI_25_PRO = "gemini-2.5-pro"

    const val BITEXING_GEMINI_25_FLASH = "gemini-2.5-flash"
    const val BITEXING_GEMINI_31_PRO_PREVIEW = "gemini-3.1-pro-preview"

    /** 一键预设 Bitexing 时默认选 flash，便于先通请求 */
    const val BITEXING_DEFAULT_MODEL = BITEXING_GEMINI_25_FLASH

    /** See BigModel console for current ids. */
    const val DEFAULT_GLM_MODEL = "glm-4.7-flash"

    const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"

    /** 阿里云百炼 DashScope OpenAI-compatible API ([docs](https://help.aliyun.com/zh/model-studio/first-api-call-to-qwen)). */
    const val DASHSCOPE_OPENAI_COMPAT_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    const val DEFAULT_QWEN_MODEL = "qwen-plus"
    const val QWEN_MAX = "qwen-max"
    const val QWEN_TURBO = "qwen-turbo"
    const val QWEN_PLUS = "qwen-plus"

    /** MiniMax M2 系列（OpenAI SDK 模型 id，以控制台为准） */
    const val MINIMAX_M2_7 = "MiniMax-M2.7"

    const val MINIMAX_M2_7_HIGHSPEED = "MiniMax-M2.7-highspeed"
    const val MINIMAX_M2_5 = "MiniMax-M2.5"
    const val MINIMAX_M2_5_HIGHSPEED = "MiniMax-M2.5-highspeed"
    const val MINIMAX_M2_1 = "MiniMax-M2.1"
    const val MINIMAX_M2_1_HIGHSPEED = "MiniMax-M2.1-highspeed"
    const val MINIMAX_M2 = "MiniMax-M2"

    const val MINIMAX_DEFAULT_MODEL = MINIMAX_M2_7
}
