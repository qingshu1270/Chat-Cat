package com.chatcat.data.holiday

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayParseTest {

    private val sample = """
        {
          "code": 0,
          "holiday": {
            "01-01": {"holiday": true, "name": "元旦", "date": "2026-01-01"},
            "01-04": {"holiday": false, "name": "元旦后补班", "date": "2026-01-04"},
            "10-01": {"holiday": true, "name": "国庆节"},
            "10-02": {"holiday": true, "name": "国庆节"}
          }
        }
    """.trimIndent()

    @Test
    fun `只取 holiday 为 true 的日期`() {
        val dates = HolidayRepository.parseYear(sample, 2026)
        assertEquals(
            setOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
            ),
            dates,
        )
    }

    @Test
    fun `异常响应返回空集合而不是崩溃`() {
        assertTrue(HolidayRepository.parseYear("not json", 2026).isEmpty())
        assertTrue(HolidayRepository.parseYear("""{"code":0}""", 2026).isEmpty())
    }

    @Test
    fun `缓存字符串可以还原`() {
        val raw = "2026-01-01,2026-10-01,坏数据"
        assertEquals(
            setOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 1)),
            com.chatcat.domain.model.PeakValley.parseHolidays(raw),
        )
    }
}
