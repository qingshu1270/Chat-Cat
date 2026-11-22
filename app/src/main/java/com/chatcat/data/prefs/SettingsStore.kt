package com.chatcat.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val thinking: Boolean = true,
    val search: Boolean = false,
    /**
     * 思考链展示方式：
     * - [THINKING_LIMITED] 思考时限高展开、内容流式滚动，结束后自动收起（默认，贴近官方）
     * - [THINKING_COLLAPSED] 默认收起，只看「已思考（用时 N 秒）」
     * - [THINKING_FULL] 默认展开全部，跟随正文一起滚动
     */
    val thinkingDisplay: String = THINKING_LIMITED,
    val lastSessionId: String? = null,
    /** system / light / dark */
    val theme: String = "system",
    /** 导入历史时用户选择保留的显示名，可为空；仅本机保存 */
    val displayName: String = "",
    /** 字号系数：1.0 为标准；官方为连续滑块（T-63），区间 0.8 ~ 1.5 */
    val fontScale: Float = 1.0f,
    /** 余额预警线（CNY）：低于此值界面用提示色，并展示「余额不足」小狮子 */
    val balanceWarn: Float = DEFAULT_BALANCE_WARN,
    /** 已同意的用户协议版本；小于当前版本时需要重新确认 */
    val termsVersion: Int = 0,
    /** 法定节假日（逗号分隔的 yyyy-MM-dd），来自在线数据源，本地缓存 */
    val holidayDates: String = "",
    /** 上次拉取节假日数据的时间（毫秒） */
    val holidayFetchedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        const val MODEL_FLASH = "deepseek-flash"
        const val MODEL_PRO = "deepseek-v4-pro"
        const val FONT_SCALE_MIN = 0.8f
        const val FONT_SCALE_MAX = 1.5f
        const val THINKING_LIMITED = "limited"
        const val THINKING_COLLAPSED = "collapsed"
        const val THINKING_FULL = "full"
        const val DEFAULT_BALANCE_WARN = 10f
        const val BALANCE_WARN_MIN = 1f
        const val BALANCE_WARN_MAX = 500f
    }
}

/**
 * 一个已保存的 API Key（账户）。
 * Key 只存在于 EncryptedSharedPreferences，界面永远只显示 [masked]。
 */
@Serializable
data class ApiKeyEntry(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: Long = 0L,
) {
    /** `sk-ab…wxyz`：仅用于界面识别，绝不展示完整 Key */
    val masked: String
        get() = when {
            key.length <= 10 -> "sk-…"
            else -> key.take(5) + "…" + key.takeLast(4)
        }
}

/**
 * 设置与 API Key。
 * Key 存 EncryptedSharedPreferences；若设备不支持（Keystore 异常），退化为普通
 * SharedPreferences 并在设置页提示，绝不写入日志。
 *
 * 多账户：Key 以 JSON 数组保存在同一加密文件里，`api_key_current` 指向当前使用项。
 * 旧版本的单条 `api_key` 会在首次读取时迁移为名为「默认 Key」的账户。
 */
class SettingsStore(private val context: Context) {

    private val keyPrefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_key",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse {
        context.getSharedPreferences("fallback_key", Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 解密+解析有成本，进程内缓存；每次写操作后更新 */
    @Volatile
    private var cache: List<ApiKeyEntry>? = null

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toSettings() }

    /**
     * 唯一的「Preferences → AppSettings」映射，读与写共用。
     * 之前 [update] 里另手写了一份，字段一多就容易漂：漏掉的字段会被按默认值写回去。
     */
    private fun Preferences.toSettings(): AppSettings = AppSettings(
        baseUrl = this[KEY_BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
        model = this[KEY_MODEL] ?: AppSettings.DEFAULT_MODEL,
        thinking = this[KEY_THINKING] ?: true,
        search = this[KEY_SEARCH] ?: false,
        thinkingDisplay = this[KEY_THINKING_DISPLAY]
            ?: if (this[KEY_AUTO_COLLAPSE] == false) AppSettings.THINKING_FULL else AppSettings.THINKING_LIMITED,
        lastSessionId = this[KEY_LAST_SESSION],
        theme = this[KEY_THEME] ?: "system",
        displayName = this[KEY_DISPLAY_NAME].orEmpty(),
        fontScale = this[KEY_FONT_SCALE] ?: 1.0f,
        balanceWarn = this[KEY_BALANCE_WARN] ?: AppSettings.DEFAULT_BALANCE_WARN,
        termsVersion = this[KEY_TERMS_VERSION] ?: 0,
        holidayDates = this[KEY_HOLIDAY_DATES] ?: "",
        holidayFetchedAt = this[KEY_HOLIDAY_FETCHED_AT] ?: 0L,
    )

    /** 一次性读取当前设置快照（用于启动时同步缓存类数据） */
    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { p ->
            val next = block(p.toSettings())
            p[KEY_BASE_URL] = next.baseUrl
            p[KEY_MODEL] = next.model
            p[KEY_THINKING] = next.thinking
            p[KEY_SEARCH] = next.search
            p[KEY_THINKING_DISPLAY] = next.thinkingDisplay
            p[KEY_TERMS_VERSION] = next.termsVersion
            p[KEY_HOLIDAY_DATES] = next.holidayDates
            p[KEY_HOLIDAY_FETCHED_AT] = next.holidayFetchedAt
            p[KEY_THEME] = next.theme
            p[KEY_DISPLAY_NAME] = next.displayName
            p[KEY_FONT_SCALE] = next.fontScale
            p[KEY_BALANCE_WARN] = next.balanceWarn
            next.lastSessionId?.let { p[KEY_LAST_SESSION] = it } ?: p.remove(KEY_LAST_SESSION)
        }
    }

    // ---------- API 账户 ----------

    /** 全部账户（按添加顺序），Key 仍是明文（仅内存使用，不得展示） */
    fun apiKeyEntries(): List<ApiKeyEntry> {
        cache?.let { return it }
        val list = loadKeys()
        if (list.isNotEmpty()) {
            cache = list
            return list
        }
        val migrated = migrateLegacy()
        cache = migrated
        return migrated
    }

    fun currentApiKeyId(): String {
        val list = apiKeyEntries()
        if (list.isEmpty()) return ""
        val id = keyPrefs.getString(KEY_CURRENT, "").orEmpty()
        return if (list.any { it.id == id }) id else list.first().id
    }

    val currentApiKeyEntry: ApiKeyEntry?
        get() = apiKeyEntries().firstOrNull { it.id == currentApiKeyId() }

    /** 新增账户；Key 已存在则复用该账户。返回账户 id 并设为当前。 */
    fun addApiKey(name: String, key: String): String {
        val trimmedKey = key.trim()
        val list = apiKeyEntries().toMutableList()
        val label = name.trim().ifBlank { nextDefaultName(list) }
        val index = list.indexOfFirst { it.key == trimmedKey }
        val entry = if (index >= 0) {
            // 同一 Key 重复添加：保留原命名，除非用户显式给了新名字
            list[index].copy(name = if (name.isBlank()) list[index].name else label)
        } else {
            ApiKeyEntry(
                id = UUID.randomUUID().toString(),
                name = label,
                key = trimmedKey,
                createdAt = System.currentTimeMillis(),
            )
        }
        if (index >= 0) list[index] = entry else list.add(entry)
        persist(list)
        keyPrefs.edit().putString(KEY_CURRENT, entry.id).apply()
        return entry.id
    }

    fun renameApiKey(id: String, name: String) {
        val value = name.trim()
        if (value.isEmpty()) return
        persist(apiKeyEntries().map { if (it.id == id) it.copy(name = value) else it })
    }

    /** 切换当前使用的账户；id 不存在时忽略 */
    fun selectApiKey(id: String) {
        if (apiKeyEntries().none { it.id == id }) return
        keyPrefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun deleteApiKey(id: String) {
        val before = currentApiKeyId()
        val list = apiKeyEntries().filterNot { it.id == id }
        persist(list)
        if (before == id) {
            keyPrefs.edit().apply {
                list.firstOrNull()?.let { putString(KEY_CURRENT, it.id) } ?: remove(KEY_CURRENT)
            }.apply()
        }
    }

    /** 当前使用的 Key；无账户时为空字符串。赋值等价于「新增或复用并设为当前」 */
    var apiKey: String
        get() = currentApiKeyEntry?.key.orEmpty()
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                persist(emptyList())
                keyPrefs.edit().remove(KEY_CURRENT).apply()
                return
            }
            addApiKey("", trimmed)
        }

    private fun nextDefaultName(list: List<ApiKeyEntry>): String {
        var i = list.size + 1
        while (list.any { it.name == "Key $i" }) i++
        return "Key $i"
    }

    private fun loadKeys(): List<ApiKeyEntry> {
        val raw = keyPrefs.getString(KEY_KEYS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ApiKeyEntry>>(raw) }
            .getOrDefault(emptyList())
            .filter { it.key.isNotBlank() }
    }

    private fun persist(list: List<ApiKeyEntry>) {
        keyPrefs.edit().putString(KEY_KEYS, json.encodeToString(list)).apply()
        cache = list
    }

    /** 旧版本只存了单条 api_key：迁移为「默认 Key」账户 */
    private fun migrateLegacy(): List<ApiKeyEntry> {
        val legacy = keyPrefs.getString(KEY_API, "").orEmpty().trim()
        if (legacy.isEmpty()) return emptyList()
        val entry = ApiKeyEntry(
            id = UUID.randomUUID().toString(),
            name = "默认 Key",
            key = legacy,
            createdAt = System.currentTimeMillis(),
        )
        persist(listOf(entry))
        keyPrefs.edit().putString(KEY_CURRENT, entry.id).remove(KEY_API).apply()
        return listOf(entry)
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_KEYS = "api_keys"
        const val KEY_CURRENT = "api_key_current"
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_THINKING = booleanPreferencesKey("thinking")
        val KEY_SEARCH = booleanPreferencesKey("search")
        /** 旧口径：仅用于第一次升级时推断 [AppSettings.thinkingDisplay] */
        val KEY_AUTO_COLLAPSE = booleanPreferencesKey("auto_collapse_thinking")
        val KEY_THINKING_DISPLAY = stringPreferencesKey("thinking_display")
        val KEY_TERMS_VERSION = intPreferencesKey("terms_version")
        val KEY_HOLIDAY_DATES = stringPreferencesKey("holiday_dates")
        val KEY_HOLIDAY_FETCHED_AT = longPreferencesKey("holiday_fetched_at")
        val KEY_LAST_SESSION = stringPreferencesKey("last_session")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val KEY_BALANCE_WARN = floatPreferencesKey("balance_warn")
    }
}
