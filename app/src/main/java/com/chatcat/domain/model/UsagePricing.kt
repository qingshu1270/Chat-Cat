package com.chatcat.domain.model

import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 一次（或若干次累加的）请求用量。缓存命中价与非命中价分开统计，费用按公开价目表估算。
 * 价格口径见 docs/02 §12；均为「元 / 百万 token」。
 */
data class UsageSummary(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val model: String = "",
) {
    val totalInput: Long get() = inputTokens + cacheReadTokens + cacheCreationTokens

    operator fun plus(other: UsageSummary) = UsageSummary(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cacheReadTokens = cacheReadTokens + other.cacheReadTokens,
        cacheCreationTokens = cacheCreationTokens + other.cacheCreationTokens,
        model = model,
    )

    companion object {
        fun of(records: List<UsageSummary>): UsageSummary {
            if (records.isEmpty()) return UsageSummary()
            // model 取最后一条（续写/model 切换以最后一次为准）
            val total = records.fold(UsageSummary()) { acc, r -> acc + r }
            return total.copy(model = records.last().model)
        }
    }
}

/** 公开价目表估算（docs/02 §12），实际以账单为准。 */
object UsagePricing {
    // flash（元/百万 token）：缓存命中 / 未命中 / 输出
    const val FLASH_CACHE = 0.02
    const val FLASH_INPUT = 1.0
    const val FLASH_OUTPUT = 4.0
    // pro（元/百万 token）
    const val PRO_CACHE = 0.15
    const val PRO_INPUT = 4.5
    const val PRO_OUTPUT = 13.5
    const val PER_MILLION = 1_000_000.0

    fun isPro(model: String): Boolean = model.contains("pro", ignoreCase = true)

    /** 是否高峰时段：北京时间周一至周五 9:00–12:00、14:00–18:00（×2） */
    fun isPeak(calendar: Calendar): Boolean {
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour in 9..11 || hour in 14..17
    }

    /**
     * 估算费用（元）。缓存命中按命中价，未命中输入按输入价；缓存创建按未命中价。
     * 高峰时段输入与输出 ×2（命中价在高文档中未单列，同样翻倍）。
     */
    fun estimateYuan(usage: UsageSummary, peak: Boolean = false): Double {
        val cache = usage.cacheReadTokens
        val nonCachedInput = usage.inputTokens + usage.cacheCreationTokens
        val (cacheRate, inputRate, outputRate) = if (isPro(usage.model)) {
            Triple(PRO_CACHE, PRO_INPUT, PRO_OUTPUT)
        } else {
            Triple(FLASH_CACHE, FLASH_INPUT, FLASH_OUTPUT)
        }
        val factor = if (peak) 2.0 else 1.0
        val cost = (cache * cacheRate + nonCachedInput * inputRate) / PER_MILLION * factor +
            (usage.outputTokens * outputRate) / PER_MILLION * factor
        return (cost * 100).roundToInt() / 100.0
    }
}

/** 千分位展示，如 9078 -> "9,078" */
fun formatCount(value: Long): String = value.toString().reversed()
    .chunked(3).joinToString(",").reversed()
