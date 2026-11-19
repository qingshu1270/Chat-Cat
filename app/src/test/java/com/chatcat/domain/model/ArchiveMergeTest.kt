package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMergeTest {

    @Test
    fun `标题空白与大小写不影响指纹`() {
        assertEquals(
            ArchiveMerge.fingerprint("  Hello World ", 1000L),
            ArchiveMerge.fingerprint("hello world", 1000L),
        )
    }

    @Test
    fun `创建时间不同视为不同会话`() {
        val incoming = listOf("同一个标题" to 2000L)
        val existing = listOf("同一个标题" to 1000L)
        assertEquals(listOf(0), ArchiveMerge.newIndices(incoming, existing))
    }

    @Test
    fun `已存在的会话被跳过，新会话保留原顺序`() {
        val existing = listOf("已存在" to 1L, "另一条" to 5L)
        val incoming = listOf(
            "已存在" to 1L,
            "新会话 A" to 2L,
            "新会话 B" to 3L,
            "另一条" to 5L,
        )
        val indices = ArchiveMerge.newIndices(incoming, existing)
        assertEquals(listOf(1, 2), indices)
        assertTrue(ArchiveMerge.newIndices(incoming, incoming).isEmpty())
    }
}
