package com.chatcat.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadTokens: Int = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationTokens: Int = 0,
) {
    val totalInput: Int get() = inputTokens + cacheReadTokens + cacheCreationTokens
}

/** 统一的流式事件，协议差异在这一层被抹平。 */
sealed interface StreamEvent {
    data class Started(val id: String, val model: String, val usage: Usage?) : StreamEvent

    data class BlockStarted(val index: Int, val block: StartBlock) : StreamEvent

    data class TextDelta(val index: Int, val text: String) : StreamEvent
    data class ThinkingDelta(val index: Int, val text: String) : StreamEvent
    data class SignatureDelta(val index: Int, val signature: String) : StreamEvent

    data class CitationDelta(
        val index: Int,
        val url: String,
        val title: String?,
        val citedText: String,
    ) : StreamEvent

    data class BlockStopped(val index: Int) : StreamEvent

    data class TitleReceived(val title: String) : StreamEvent

    data class Finished(val stopReason: String?, val usage: Usage?) : StreamEvent
}

sealed interface StartBlock {
    data class Text(val text: String) : StartBlock
    data class Thinking(val thinking: String, val signature: String) : StartBlock

    /** server_tool_use / web_search_tool_result / web_fetch_tool_result：M1 只记录，不渲染 */
    data class Tool(
        val type: String,
        val name: String?,
        val kind: String?,
        /** content_block 原始 JSON，用于展示搜索/网页结果 */
        val payload: String? = null,
    ) : StartBlock
}
