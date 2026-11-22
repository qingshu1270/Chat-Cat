package com.chatcat.data.holiday

import com.chatcat.data.prefs.SettingsStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 法定节假日数据源。
 *
 * 数据不在代码里写死：从 timor.tech 的公开节假日接口按年拉取 JSON，
 * 只取 `holiday == true` 的日期（调休补班日 `holiday == false` 忽略，
 * 因为按规则周末与节假日都算空闲时段）。
 *
 * 刷新策略：缓存有效期 [MIN_INTERVAL_MS]（12 小时）。
 * 启动时先读本地缓存立即生效，再在后台判断是否需要拉取；失败保留旧数据，不打扰用户。
 */
class HolidayRepository(
    private val client: OkHttpClient,
    private val store: SettingsStore,
) {

    companion object {
        const val ENDPOINT = "https://timor.tech/api/holiday/year/"
        const val MIN_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private val ZONE = ZoneId.of("Asia/Shanghai")
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        /** 解析形如 {"holiday":{"01-01":{"holiday":true,...}}} 的响应；也兼容直接给 date 字段 */
        fun parseYear(body: String, year: Int): Set<LocalDate> {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return emptySet()
            val holiday = root["holiday"] as? JsonObject ?: return emptySet()
            return holiday.entries.mapNotNull { (key, value) ->
                val item = value as? JsonObject ?: return@mapNotNull null
                val isHoliday = (item["holiday"] as? JsonPrimitive)?.booleanOrNull ?: false
                if (!isHoliday) return@mapNotNull null
                // key 是 MM-DD；item.date 是完整日期（有的版本才有）
                val dateText = (item["date"] as? JsonPrimitive)?.contentOrNull
                runCatching {
                    if (!dateText.isNullOrBlank()) LocalDate.parse(dateText)
                    else LocalDate.of(year, key.substring(0, 2).toInt(), key.substring(3, 5).toInt())
                }.getOrNull()
            }.toSet()
        }
    }

    /** 读本地缓存（返回空集表示还没有数据） */
    suspend fun cached(): Set<LocalDate> = store.snapshot().holidayDates
        .let { com.chatcat.domain.model.PeakValley.parseHolidays(it) }

    /**
     * 判断是否需要刷新并拉取。
     * @param force 手动刷新（设置页按钮）
     * @return 最新的节假日集合；失败时返回缓存
     */
    suspend fun ensureFresh(force: Boolean = false): Set<LocalDate> = withContext(Dispatchers.IO) {
        val snapshot = store.snapshot()
        val now = System.currentTimeMillis()
        val cachedSet = com.chatcat.domain.model.PeakValley.parseHolidays(snapshot.holidayDates)

        val fresh = !force &&
            snapshot.holidayFetchedAt > 0 &&
            now - snapshot.holidayFetchedAt < MIN_INTERVAL_MS
        if (fresh) return@withContext cachedSet

        val year = java.time.Instant.ofEpochMilli(now).atZone(ZONE).year
        val month = java.time.Instant.ofEpochMilli(now).atZone(ZONE).monthValue
        // 11 月起顺带把下一年的安排也拉下来（次年安排通常在年底公布）
        val years = buildList {
            add(year)
            if (month >= 11) add(year + 1)
        }

        val fetched = years.mapNotNull { y -> runCatching { fetchYear(y) }.getOrNull() }
            .flatten()
            .toSet()

        if (fetched.isNotEmpty()) {
            store.update {
                it.copy(
                    holidayDates = fetched.sorted().joinToString(",") { d -> d.toString() },
                    holidayFetchedAt = now,
                )
            }
            fetched
        } else {
            // 失败：只记时间戳，避免频繁重试；数据继续用旧缓存
            store.update { it.copy(holidayFetchedAt = now) }
            cachedSet
        }
    }

    private fun fetchYear(year: Int): Set<LocalDate> {
        val request = Request.Builder()
            .url(ENDPOINT + year)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return parseYear(body, year)
        }
    }

}
