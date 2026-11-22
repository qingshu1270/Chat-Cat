package com.chatcat.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeakValleyTest {

    @Before
    fun setUp() {
        // 节假日来自在线数据，这里注入 2026 国庆做测试
        PeakValley.setHolidays(
            setOf(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
            ),
        )
    }

    private fun at(date: LocalDate, time: String): Long =
        LocalDateTime.of(date, LocalTime.parse(time))
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()

    // 2026-11-23 是周一，2026-11-21 是周六
    private val monday: LocalDate = LocalDate.of(2026, 11, 23)
    private val saturday: LocalDate = LocalDate.of(2026, 11, 21)
    private val nationalDay: LocalDate = LocalDate.of(2026, 10, 1) // 周四，法定节假日

    @Test
    fun `工作日的高峰时段是 9-12 与 14-18`() {
        assertTrue(PeakValley.isPeak(at(monday, "09:00")))
        assertTrue(PeakValley.isPeak(at(monday, "11:59")))
        assertFalse(PeakValley.isPeak(at(monday, "12:00")))
        assertFalse(PeakValley.isPeak(at(monday, "13:30")))
        assertTrue(PeakValley.isPeak(at(monday, "14:00")))
        assertTrue(PeakValley.isPeak(at(monday, "17:59")))
        assertFalse(PeakValley.isPeak(at(monday, "18:00")))
        assertFalse(PeakValley.isPeak(at(monday, "08:59")))
    }

    @Test
    fun `周末与法定节假日全天都算空闲`() {
        assertFalse(PeakValley.isPeak(at(saturday, "10:00")))
        assertFalse(PeakValley.isPeak(at(nationalDay, "10:00")))
    }

    @Test
    fun `午休时段倒计时指向 14 点`() {
        assertEquals("01:30", PeakValley.countdown(at(monday, "12:30")))
    }

    @Test
    fun `晚上倒计时指向次日 9 点`() {
        assertEquals("14:00", PeakValley.countdown(at(monday, "19:00")))
    }

    @Test
    fun `周末倒计时指向下周一 9 点`() {
        // 周六 12:00 → 周一 09:00，共 45 小时
        assertEquals("45:00", PeakValley.countdown(at(saturday, "12:00")))
    }

    @Test
    fun `高峰中倒计时指向本场结束`() {
        assertEquals("01:00", PeakValley.countdown(at(monday, "11:00")))
        assertEquals("02:00", PeakValley.countdown(at(monday, "16:00")))
    }

    @Test
    fun `标签区分峰谷`() {
        assertEquals("当前处于梁文峰时间", PeakValley.label(at(monday, "10:00")))
        assertEquals("当前处于梁文谷时间", PeakValley.label(at(monday, "20:00")))
        assertEquals("当前处于梁文谷时间", PeakValley.label(at(saturday, "10:00")))
    }
}
