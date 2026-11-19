package com.chatcat.data.remote

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseReaderTest {

    @Test
    fun `parses named events and multi-line data`() {
        val raw = buildString {
            append("event: message_start\n")
            append("data: {\"type\":\"message_start\",\n")
            append("data: \"id\":\"abc\"}\n")
            append("\n")
            append(": keep-alive\n")
            append("\n")
            append("data: {\"type\":\"message_stop\"}\n")
            append("\n")
        }
        val frames = SseReader(Buffer().writeUtf8(raw)).frames().toList()

        assertEquals(2, frames.size)
        assertEquals("message_start", frames[0].event)
        assertEquals("{\"type\":\"message_start\",\n\"id\":\"abc\"}", frames[0].data)
        assertNull(frames[1].event)
        assertEquals("{\"type\":\"message_stop\"}", frames[1].data)
    }

    @Test
    fun `ignores comment-only frames`() {
        val raw = ": ping\n\n: ping\n\n"
        val frames = SseReader(Buffer().writeUtf8(raw)).frames().toList()
        assertEquals(0, frames.size)
    }

    @Test
    fun `keeps trailing frame without blank line`() {
        val raw = "event: message_delta\ndata: {\"type\":\"message_delta\"}"
        val frames = SseReader(Buffer().writeUtf8(raw)).frames().toList()
        assertEquals(1, frames.size)
        assertEquals("message_delta", frames[0].event)
    }
}
