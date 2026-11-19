package com.chatcat.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses official balance response`() {
        // 官方 api-docs 示例结构：字符串金额
        val body = """
            {"is_available":true,"balance_infos":[
              {"currency":"CNY","total_balance":"18.32","granted_balance":"5.00","topped_up_balance":"13.32"}
            ]}
        """.trimIndent()
        val resp = json.decodeFromString<BalanceResponse>(body)
        assertTrue(resp.isAvailable)
        val info = resp.primary()
        assertNotNull(info)
        assertEquals("CNY", info!!.currency)
        assertEquals("18.32", info.totalBalance)
        assertEquals("5.00", info.grantedBalance)
        assertEquals("13.32", info.toppedUpBalance)
    }

    @Test
    fun `primary prefers CNY over USD`() {
        val body = """
            {"is_available":true,"balance_infos":[
              {"currency":"USD","total_balance":"1.00","granted_balance":"0","topped_up_balance":"1.00"},
              {"currency":"CNY","total_balance":"7.00","granted_balance":"0","topped_up_balance":"7.00"}
            ]}
        """.trimIndent()
        val resp = json.decodeFromString<BalanceResponse>(body)
        assertEquals("CNY", resp.primary()!!.currency)
    }

    @Test
    fun `unavailable with empty infos yields null primary`() {
        val resp = json.decodeFromString<BalanceResponse>("""{"is_available":false,"balance_infos":[]}""")
        assertFalse(resp.isAvailable)
        assertNull(resp.primary())
    }

    @Test
    fun `tolerates unknown fields`() {
        val resp = json.decodeFromString<BalanceResponse>(
            """{"is_available":true,"future_field":123,"balance_infos":[]}""",
        )
        assertTrue(resp.isAvailable)
    }
}
