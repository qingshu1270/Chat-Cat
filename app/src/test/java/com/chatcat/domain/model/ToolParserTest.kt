package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolParserTest {

    @Test
    fun `search result keeps http sources and drops others`() {
        val payload = """
            {"type":"web_search_tool_result","content":[
              {"type":"web_search_result","title":"A","url":"https://a.example","snippet":"sa","site_name":"a.example"},
              {"type":"web_search_result","title":"B","url":"javascript:alert(1)","snippet":"sb"}
            ]}
        """.trimIndent()

        val info = ToolParser.parse("", payload)!!
        assertEquals(1, info.sources.size)
        assertEquals("A", info.sources.first().title)
        assertTrue(info.isSearch)
    }

    @Test
    fun `fetch result exposes title and url`() {
        val payload = """
            {"type":"web_fetch_tool_result","content":{
              "type":"web_fetch_result","url":"https://b.example/page","title":"B 页面"
            }}
        """.trimIndent()

        val info = ToolParser.parse("", payload)!!
        assertEquals("B 页面", info.title)
        assertEquals("https://b.example/page", info.url)
        assertTrue(info.isFetch)
    }

    @Test
    fun `server tool use exposes queries`() {
        val payload = """
            {"type":"server_tool_use","name":"deepseek_web_search",
             "input":{"action":"search","queries":["q1","q2"]}}
        """.trimIndent()

        val info = ToolParser.parse("", payload)!!
        assertEquals(listOf("q1", "q2"), info.queries)
        assertEquals("wip", info.status)
    }

    @Test
    fun `unknown or malformed payload is ignored`() {
        assertNull(ToolParser.parse("", null))
        assertNull(ToolParser.parse("", "not json"))
        assertNull(ToolParser.parse("", """{"type":"unknown"}"""))
    }
}
