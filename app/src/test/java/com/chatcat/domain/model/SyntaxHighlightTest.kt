package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SyntaxHighlightTest {

    @Test
    fun `数字与关键字分别着色`() {
        val tokens = SyntaxHighlight.tokenize("val x = 1L")
        assertEquals(
            listOf(
                "val" to SyntaxHighlight.Kind.KEYWORD,
                "1" to SyntaxHighlight.Kind.NUMBER,
            ),
            tokens.filter { it.kind == SyntaxHighlight.Kind.KEYWORD || it.kind == SyntaxHighlight.Kind.NUMBER }
                .map { it.text to it.kind },
        )
    }
}
