package com.chatcat.domain.model

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationTest {

    private val searchPayload = buildJsonObject {
        put("type", "web_search_tool_result")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "web_search_result")
                put("title", "A")
                put("url", "https://a.example")
                put("site_name", "a.example")
            })
            add(buildJsonObject {
                put("type", "web_search_result")
                put("title", "B")
                put("url", "https://b.example")
                put("site_name", "b.example")
            })
        })
    }.toString()

    private fun message(citations: List<Citation>): UiMessage = UiMessage(
        id = "a1",
        role = Role.ASSISTANT,
        status = MessageStatus.DONE,
        blocks = listOf(
            UiBlock("t0", 0, BlockType.TEXT, "答案", citations = citations),
            UiBlock("tool1", 1, BlockType.TOOL, "", payload = searchPayload),
        ),
    )

    @Test
    fun `sources are deduped in block order`() {
        val msg = message(emptyList())
        assertEquals(listOf("https://a.example", "https://b.example"), msg.sources.map { it.url })
    }

    @Test
    fun `citation markers follow source numbers`() {
        val msg = message(
            listOf(
                Citation("https://b.example"),
                Citation("https://a.example"),
            ),
        )
        assertEquals("答案[¹](https://a.example)[²](https://b.example)", msg.textWithCitations)
    }

    @Test
    fun `unknown citation url is ignored`() {
        val msg = message(listOf(Citation("https://other.example")))
        assertEquals("答案", msg.textWithCitations)
    }

    @Test
    fun `codec round trips and ignores non text payload`() {
        val encoded = CitationCodec.encode(listOf(Citation("https://a.example", "A")))!!
        val decoded = CitationCodec.decode("text", encoded)
        assertEquals(1, decoded.size)
        assertEquals("https://a.example", decoded.first().url)
        assertEquals("A", decoded.first().title)

        assertTrue(CitationCodec.decode("thinking", encoded).isEmpty())
        assertNull(CitationCodec.encode(emptyList()))
    }
}
