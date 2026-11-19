package com.chatcat.domain.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePricingTest {

    private fun usage(
        input: Long = 0, output: Long = 0, cache: Long = 0,
        cacheCreation: Long = 0, model: String = AppSettings_TEST_MODEL,
    ) = UsageSummary(input, output, cache, cacheCreation, model)

    companion object {
        const val AppSettings_TEST_MODEL = "deepseek-flash"
    }

    @Test
    fun `totalInput sums all input categories`() {
        val u = usage(input = 100, cache = 40, cacheCreation = 10)
        assertEquals(150L, u.totalInput)
    }

    @Test
    fun `plus accumulates across paginated records and keeps last model`() {
        val result = UsageSummary.of(
            listOf(
                usage(input = 100, output = 20, model = "deepseek-flash"),
                usage(input = 50, output = 10, cache = 5, model = "deepseek-v4-pro"),
            ),
        )
        assertEquals(150L, result.inputTokens)
        assertEquals(30L, result.outputTokens)
        assertEquals(5L, result.cacheReadTokens)
        assertEquals("deepseek-v4-pro", result.model)
    }

    @Test
    fun `empty aggregate is zero`() {
        assertEquals(UsageSummary(), UsageSummary.of(emptyList()))
    }

    @Test
    fun `flash off-peak cost with cache hit`() {
        // 缓存命中 4096 @0.02, 未命中 4982 @1, 输出 1686 @4（每分钟）
        val u = usage(input = 4_982, cache = 4_096, output = 1_686)
        val cost = UsagePricing.estimateYuan(u, peak = false)
        val expected = (4_096 * 0.02 + 4_982 * 1.0) / 1_000_000.0 + (1_686 * 4.0) / 1_000_000.0
        assertEquals((expected * 100).toInt() / 100.0, cost, 0.01)
    }

    @Test
    fun `peak doubles cost`() {
        val u = usage(input = 1_000_000, output = 1_000_000)
        val off = UsagePricing.estimateYuan(u, peak = false)
        val on = UsagePricing.estimateYuan(u, peak = true)
        assertEquals(off * 2, on, 0.01)
    }

    @Test
    fun `pro prices higher than flash`() {
        val u = usage(input = 1_000_000, output = 1_000_000, model = "deepseek-v4-pro")
        assertTrue(UsagePricing.estimateYuan(u) > UsagePricing.estimateYuan(u.copy(model = "deepseek-flash")))
    }

    @Test
    fun `isPro detects model name case-insensitively`() {
        assertTrue(UsagePricing.isPro("deepseek-v4-pro"))
        assertTrue(UsagePricing.isPro("DeepSeek-PRO"))
        assertFalse(UsagePricing.isPro("deepseek-flash"))
    }

    @Test
    fun `isPeak weekday business hours only`() {
        val mon = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
        assertTrue(UsagePricing.isPeak(mon.apply { set(Calendar.HOUR_OF_DAY, 10) }))
        assertTrue(UsagePricing.isPeak(mon.apply { set(Calendar.HOUR_OF_DAY, 15) }))
        assertFalse(UsagePricing.isPeak(mon.apply { set(Calendar.HOUR_OF_DAY, 13) }))
        assertFalse(UsagePricing.isPeak(mon.apply { set(Calendar.HOUR_OF_DAY, 20) }))
        val sat = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY); set(Calendar.HOUR_OF_DAY, 10)
        }
        assertFalse(UsagePricing.isPeak(sat))
    }

    @Test
    fun `formatCount adds thousands separators`() {
        assertEquals("9,078", formatCount(9078))
        assertEquals("1,000,000", formatCount(1_000_000))
        assertEquals("0", formatCount(0))
    }
}
