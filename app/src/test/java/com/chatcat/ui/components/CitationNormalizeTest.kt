package com.chatcat.ui.components

import com.chatcat.domain.model.ToolSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CitationNormalizeTest {

    private val sources = listOf(
        ToolSource(title = "A", url = "https://a.com"),
        ToolSource(title = "B", url = "https://b.com"),
    )

    @Test
    fun `行首角标后移，标题语法得以保留`() {
        val input = "[440](https://a.com)### 服务端实现要点"
        assertEquals("### 服务端实现要点[¹](https://a.com)", normalizeCitations(input, sources))
    }

    @Test
    fun `按来源顺序重排为 1 和 2`() {
        val input = "结论一[9](https://b.com)，结论二[7](https://a.com)"
        assertEquals(
            "结论一[²](https://b.com)，结论二[¹](https://a.com)",
            normalizeCitations(input, sources),
        )
    }

    @Test
    fun `同一来源复用同一序号`() {
        val input = "甲[5](https://a.com) 乙[6](https://a.com)"
        assertEquals("甲[¹](https://a.com) 乙[¹](https://a.com)", normalizeCitations(input, sources))
    }

    @Test
    fun `无角标文本原样返回`() {
        val input = "普通段落，没有引用"
        assertEquals(input, normalizeCitations(input, sources))
    }
}
