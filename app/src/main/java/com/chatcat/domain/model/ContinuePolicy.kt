package com.chatcat.domain.model

/**
 * 续写（continue）判定，对齐 chat-demo 的 canContinueReply：
 * - 只对「被截断 / 用户停止」且有正文的助手消息开放；
 * - 用过联网工具、拒答、流式中、错误态一律不可续写；
 * - 空正文不可续写。
 */
object ContinuePolicy {

    fun canContinue(message: UiMessage): Boolean {
        if (message.role != Role.ASSISTANT) return false
        if (message.isStreaming) return false
        if (message.text.isBlank()) return false
        if (message.usedTools) return false
        if (message.stopReason == "refusal") return false
        return when (message.status) {
            MessageStatus.STOPPED -> true
            MessageStatus.DONE -> message.stopReason == "max_tokens"
            else -> false
        }
    }
}
