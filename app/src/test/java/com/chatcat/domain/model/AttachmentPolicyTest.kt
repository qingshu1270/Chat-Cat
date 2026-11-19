package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPolicyTest {

    @Test
    fun `rejects unsupported extension`() {
        assertEquals("包含不支持的文件类型", AttachmentPolicy.validate(listOf("a.exe" to 10L), 0))
    }

    @Test
    fun `layout document allows 64mb but user data only 32mb`() {
        val big = 40L * 1024 * 1024
        assertNull(AttachmentPolicy.validate(listOf("a.pdf" to big), 0))
        assertEquals("单个文件不能超过 32MB", AttachmentPolicy.validate(listOf("a.csv" to big), 0))
    }

    @Test
    fun `counts are capped at 50`() {
        val files = List(51) { "f$it.txt" to 10L }
        assertEquals("最多上传 50 个文件", AttachmentPolicy.validate(files, 0))
        assertEquals("最多上传 50 个文件", AttachmentPolicy.validate(listOf("x.txt" to 10L), 50))
    }

    @Test
    fun `purpose splits images layout and user data`() {
        assertNull(AttachmentPolicy.purpose("a.png", true))
        assertEquals("rich_layout_recognition", AttachmentPolicy.purpose("a.pdf", false))
        assertEquals("user_data", AttachmentPolicy.purpose("a.csv", false))
    }

    @Test
    fun `pro model does not support images`() {
        assertFalse(AttachmentPolicy.supportsImages("deepseek-v4-pro"))
        assertTrue(AttachmentPolicy.supportsImages("deepseek-flash"))
    }

    @Test
    fun `formats size`() {
        assertEquals("512 B", AttachmentPolicy.formatSize(512))
        assertEquals("1.0 KB", AttachmentPolicy.formatSize(1024))
        assertEquals("1.5 MB", AttachmentPolicy.formatSize(1024L * 1024 * 3 / 2))
    }
}
