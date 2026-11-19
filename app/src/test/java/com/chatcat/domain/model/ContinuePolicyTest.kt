package com.chatcat.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuePolicyTest {

    private fun message(
        status: MessageStatus,
        stopReason: String? = "end_turn",
        text: String = "已有回答",
        usedTools: Boolean = false,
    ) = UiMessage(
        id = "a1",
        role = Role.ASSISTANT,
        status = status,
        stopReason = stopReason,
        usedTools = usedTools,
        blocks = listOf(UiBlock("b1", 0, BlockType.TEXT, text)),
    )

    @Test
    fun `max_tokens truncation can continue`() {
        assertTrue(ContinuePolicy.canContinue(message(MessageStatus.DONE, "max_tokens")))
    }

    @Test
    fun `user stopped can continue`() {
        assertTrue(ContinuePolicy.canContinue(message(MessageStatus.STOPPED, "aborted")))
    }

    @Test
    fun `normal end cannot continue`() {
        assertFalse(ContinuePolicy.canContinue(message(MessageStatus.DONE, "end_turn")))
    }

    @Test
    fun `tool reply cannot continue`() {
        assertFalse(
            ContinuePolicy.canContinue(
                message(MessageStatus.DONE, "max_tokens", usedTools = true),
            ),
        )
    }

    @Test
    fun `refusal and blank cannot continue`() {
        assertFalse(ContinuePolicy.canContinue(message(MessageStatus.REFUSED, "refusal")))
        assertFalse(ContinuePolicy.canContinue(message(MessageStatus.STOPPED, "aborted", text = " ")))
    }

    @Test
    fun `streaming or failed cannot continue`() {
        assertFalse(ContinuePolicy.canContinue(message(MessageStatus.STREAMING, null)))
        assertFalse(ContinuePolicy.canContinue(message(MessageStatus.FAILED, null)))
    }
}
