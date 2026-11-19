package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardParsersTest {

    // ---------- 导出链接 ----------

    @Test
    fun `从中文提示文本里取导出链接`() {
        val text = "请复制链接：https://cdn.deepseek.com/chat/export/9f3.zip?sign=a1b2 有效期 24 小时"
        assertEquals(
            "https://cdn.deepseek.com/chat/export/9f3.zip?sign=a1b2",
            ExportLinkParser.find(text),
        )
    }

    @Test
    fun `链接结尾的中文标点会被裁掉`() {
        val text = "下载链接：https://cdn.deepseek.com/export/a.zip?sig=1，请尽快下载。"
        assertEquals("https://cdn.deepseek.com/export/a.zip?sig=1", ExportLinkParser.find(text))
    }

    @Test
    fun `普通对话页链接不当成导出包`() {
        assertNull(ExportLinkParser.find("https://chat.deepseek.com/a/chat/s/abc123"))
    }

    @Test
    fun `伪装域名被拒绝`() {
        assertNull(ExportLinkParser.find("https://deepseek.com.evil.example/export/a.zip"))
        assertNull(ExportLinkParser.find("https://notdeepseek.com/export/a.zip"))
    }

    @Test
    fun `http 链接升级为 https`() {
        assertEquals(
            "https://cdn.deepseek.com/export/a.zip",
            ExportLinkParser.find("http://cdn.deepseek.com/export/a.zip"),
        )
    }

    @Test
    fun `多个候选优先 zip`() {
        val text = "https://chat.deepseek.com/download/help 和 https://cdn.deepseek.com/e/x.zip"
        assertEquals("https://cdn.deepseek.com/e/x.zip", ExportLinkParser.find(text))
    }

    @Test
    fun `导出接口不带 zip 后缀也能识别`() {
        val text = "https://chat.deepseek.com/api/v0/chat/export/download?token=xyz"
        assertEquals(text, ExportLinkParser.find(text))
    }

    @Test
    fun `空文本与无链接返回 null`() {
        assertNull(ExportLinkParser.find(null))
        assertNull(ExportLinkParser.find("   "))
        assertNull(ExportLinkParser.find("没有链接的一段文字"))
    }

    @Test
    fun `域名判定只认官方后缀`() {
        assertTrue(ExportLinkParser.isOfficialHost("deepseek.com"))
        assertTrue(ExportLinkParser.isOfficialHost("cdn.deepseek.com"))
        assertFalse(ExportLinkParser.isOfficialHost("deepseek.com.cn"))
        assertFalse(ExportLinkParser.isOfficialHost("evil-deepseek.com"))
    }

    @Test
    fun `对象存储签名直链也能识别`() {
        // 官方导出包常放在对象存储/CDN，域名不是 deepseek.com，必须仍能识别
        val text =
            "https://ds-export.oss-cn-hangzhou.aliyuncs.com/chat/2026/abc.zip" +
                "?OSSAccessKeyId=LTAI5t&Signature=abc%3D&Expires=1760000000"
        assertEquals(text, ExportLinkParser.find(text))
    }

    @Test
    fun `同为 https 的普通网页链接不当成导出包`() {
        assertNull(ExportLinkParser.find("https://example.com/download/help"))
        assertNull(ExportLinkParser.find("https://www.deepseek.com/"))
    }

    @Test
    fun `对象存储上的官方导出链接不被误判为钓鱼域名`() {
        // 真实导出包的 host 形如 deepseek-*-prod.obs.cn-*.myhuaweicloud.com：
        // bucket 前缀含 deepseek，但注册域是云厂商，必须放行
        val url = "https://deepseek-history-export-prod.obs.cn-east-3.myhuaweicloud.com/" +
            "0f0e1d2c-1111-4222-8333-444455556666.zip" +
            "?response-content-disposition=attachment%3B%20filename%3Ddeepseek_data-2026-01-01.zip" +
            "&Expires=1795534865&AccessKeyId=TESTACCESSKEYID&Signature=abc%2Fdef%3D"
        assertEquals(url, ExportLinkParser.find(url))
        assertEquals(url, ExportLinkParser.findLoose(url))
        assertTrue(ExportLinkParser.looksDownloadable(url))
    }

    @Test
    fun `bucket 前缀含 deepseek 不算冒充，注册域冒充才算`() {
        assertFalse(
            ExportLinkParser.isImpersonation(
                "deepseek-history-export-prod.obs.cn-east-3.myhuaweicloud.com",
            ),
        )
        assertTrue(ExportLinkParser.isTrustedHosting("x.obs.cn-east-3.myhuaweicloud.com"))
        assertTrue(ExportLinkParser.isImpersonation("deepseek-chat.cn"))
        assertTrue(ExportLinkParser.isImpersonation("deepseek.com.evil.example"))
    }

    @Test
    fun `无 zip 无签名的陌生域名仍被拒绝`() {
        assertNull(ExportLinkParser.find("https://randombucket.s3.amazonaws.com/readme.txt"))
    }

    @Test
    fun `手动粘贴接受任意 https 链接`() {
        val url = "https://chat.deepseek.com/api/v0/chat/export/download?token=x"
        assertEquals(url, ExportLinkParser.findLoose("复制这段：$url 谢谢"))
        assertNull(ExportLinkParser.findLoose("没有链接"))
    }

    // ---------- API Key ----------

    @Test
    fun `从粘贴内容里取出 key`() {
        val key = "sk-1234567890abcdefghijklmn"
        assertEquals(key, ApiKeyParser.find("我的 Key：$key\n请勿外传"))
        assertEquals(key, ApiKeyParser.find(key))
    }

    @Test
    fun `短串与非法前缀不算 key`() {
        assertNull(ApiKeyParser.find("sk-short"))
        assertNull(ApiKeyParser.find("ak-1234567890abcdefghijklmn"))
        assertNull(ApiKeyParser.find(""))
    }

    @Test
    fun `looksLikeKey 只接受完整形态`() {
        assertTrue(ApiKeyParser.looksLikeKey("sk-1234567890abcdefghijklmn"))
        assertTrue(ApiKeyParser.looksLikeKey("  sk-1234567890abcdefghijklmn  "))
        assertFalse(ApiKeyParser.looksLikeKey("sk-123"))
        assertFalse(ApiKeyParser.looksLikeKey("hello"))
    }
}
