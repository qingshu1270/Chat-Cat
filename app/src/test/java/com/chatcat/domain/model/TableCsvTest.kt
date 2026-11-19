package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TableCsvTest {

    @Test
    fun `encodes header and rows`() {
        val csv = TableCsv.encode(
            listOf("名称", "值"),
            listOf(listOf("A", "1"), listOf("B", "2")),
        )
        assertEquals("名称,值\r\nA,1\r\nB,2", csv)
    }

    @Test
    fun `quotes fields with comma quote or newline`() {
        assertEquals("\"a,b\"", TableCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", TableCsv.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", TableCsv.escape("line1\nline2"))
    }

    @Test
    fun `plain field unchanged`() {
        assertEquals("hello", TableCsv.escape("hello"))
    }
}
