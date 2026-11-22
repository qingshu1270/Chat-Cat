package com.chatcat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskClassificationTest {

    @Test
    fun `SSE 风控帧的常见表述都能识别`() {
        assertTrue(ApiException.isRiskMessage("content_filter"))
        assertTrue(ApiException.isRiskMessage(null, "Content Exists Risk"))
        assertTrue(ApiException.isRiskMessage(null, "内容存在风险"))
        assertTrue(ApiException.isRiskMessage("safety_check", "blocked"))
    }

    @Test
    fun `普通错误不会被误判成风控`() {
        assertFalse(ApiException.isRiskMessage("server_error", "internal error"))
        assertFalse(ApiException.isRiskMessage(null, "context too long"))
        assertFalse(ApiException.isRiskMessage(null, null))
    }

    @Test
    fun `HTTP 400 命中风控归为 CONTENT_RISK`() {
        val e = ApiException.fromHttp(400, """{"error":{"message":"Content Exists Risk"}}""")
        assertEquals(ApiException.Kind.CONTENT_RISK, e.kind)
    }

    @Test
    fun `HTTP 403 带风控关键词也归为 CONTENT_RISK`() {
        val e = ApiException.fromHttp(403, """{"error":{"type":"content_filter"}}""")
        assertEquals(ApiException.Kind.CONTENT_RISK, e.kind)
    }

    @Test
    fun `普通 401 仍是鉴权失败`() {
        val e = ApiException.fromHttp(401, """{"error":{"message":"invalid api key"}}""")
        assertEquals(ApiException.Kind.AUTH, e.kind)
    }
}
