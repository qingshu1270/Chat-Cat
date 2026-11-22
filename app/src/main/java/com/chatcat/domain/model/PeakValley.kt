package com.chatcat.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 峰谷时段（错峰优惠）。
 *
 * 官方规则：**空闲时段价格为高峰时段价格的一半**。
 * 高峰时段 = 北京时间周一至周五（不含中国法定节假日）09:00–12:00、14:00–18:00；
 * 其余时段（含周末、法定节假日全天）都是空闲时段。
 *
 * 法定节假日以国务院办公厅公布为准，这里内置一份日期表；跨年后需要更新 [HOLIDAYS]。
 */
object PeakValley {

    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private val MORNING_START: LocalTime = LocalTime.of(9, 0)
    private val MORNING_END: LocalTime = LocalTime.of(12, 0)
    private val AFTERNOON_START: LocalTime = LocalTime.of(14, 0)
    private val AFTERNOON_END: LocalTime = LocalTime.of(18, 0)

    /**
     * 中国法定节假日（由在线数据源拉取后写入，见 HolidayRepository）。
     * 不写死在代码里：默认空集合，只有拉取成功后才会排除节假日。
     */
    @Volatile
    private var holidays: Set<LocalDate> = emptySet()

    /** 更新节假日集合（拉起 / 缓存加载时调用） */
    fun setHolidays(dates: Set<LocalDate>) {
        holidays = dates
    }

    /** 解析 "2026-01-01,2026-01-02" 形式的缓存字符串 */
    fun parseHolidays(raw: String): Set<LocalDate> =
        raw.split(',').mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet()

    fun isHoliday(date: LocalDate): Boolean = date in holidays

    /** 是否处于高峰时段 */
    fun isPeak(now: Long = System.currentTimeMillis()): Boolean {
        val zoned = Instant.ofEpochMilli(now).atZone(ZONE)
        val date = zoned.toLocalDate()
        val time = zoned.toLocalTime()
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        if (isHoliday(date)) return false
        val morning = !time.isBefore(MORNING_START) && time.isBefore(MORNING_END)
        val afternoon = !time.isBefore(AFTERNOON_START) && time.isBefore(AFTERNOON_END)
        return morning || afternoon
    }

    fun isValley(now: Long = System.currentTimeMillis()): Boolean = !isPeak(now)

    /** 「梁文峰时间」/「梁文谷时间」 */
    fun label(now: Long = System.currentTimeMillis()): String =
        if (isPeak(now)) "当前处于梁文峰时间" else "当前处于梁文谷时间"

    /** 下一个高峰时段的起点（用于计算距离下次切换） */
    private fun nextPeakStart(from: Long): Long {
        val zoned = Instant.ofEpochMilli(from).atZone(ZONE)
        var day = zoned.toLocalDate()
        var time = zoned.toLocalTime()
        // 当天剩余的高峰起点
        while (true) {
            val weekday = day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
            if (weekday && !isHoliday(day)) {
                val candidates = listOf(MORNING_START, AFTERNOON_START)
                val hit = candidates.firstOrNull { it.isAfter(time) }
                if (hit != null) {
                    return day.atTime(hit).atZone(ZONE).toInstant().toEpochMilli()
                }
            }
            day = day.plusDays(1)
            time = LocalTime.MIDNIGHT
        }
    }

    /** 距离下次峰谷切换的毫秒数 */
    fun millisUntilSwitch(now: Long = System.currentTimeMillis()): Long {
        val zoned = Instant.ofEpochMilli(now).atZone(ZONE)
        val date = zoned.toLocalDate()
        val time = zoned.toLocalTime()
        val target = when {
            // 高峰中：上午场 → 12:00；下午场 → 18:00
            isPeak(now) && time.isBefore(MORNING_END) -> date.atTime(MORNING_END)
            isPeak(now) -> date.atTime(AFTERNOON_END)
            // 空闲中：午休 → 14:00；其余情况 → 下一个高峰起点
            !time.isBefore(MORNING_END) && time.isBefore(AFTERNOON_START) &&
                date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY &&
                !isHoliday(date) -> date.atTime(AFTERNOON_START)
            else -> null
        }
        val targetMillis = target?.atZone(ZONE)?.toInstant()?.toEpochMilli() ?: nextPeakStart(now)
        return (targetMillis - now).coerceAtLeast(0)
    }

    /** 倒计时文案：小时数:分钟数，如 03:25 */
    fun countdown(now: Long = System.currentTimeMillis()): String {
        val totalMinutes = millisUntilSwitch(now) / 60_000L
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    /** 一行提示：当前处于梁文峰时间 · 距下次切换 03:25 */
    fun hint(now: Long = System.currentTimeMillis()): String =
        "${label(now)} · 距下次切换 ${countdown(now)}"
}
