package com.chatcat.domain.model

import java.net.URI

/**
 * 从任意剪贴板文本中提取 DeepSeek 官方历史导出下载链接。
 *
 * 纯函数、无 Android 依赖，便于单测。
 *
 * 判定策略（v1.1 起）：
 * 1. 官方导出包**不在** `deepseek.com` 域下——它是对象存储的一次性签名直链
 *    （华为云 OBS `*.obs.cn-*.myhuaweicloud.com`、阿里云 OSS、AWS S3 等），
 *    bucket 名里通常带 `deepseek`/`export`/`history` 字样。
 * 2. 因此域名判定分三档：[isOfficialHost] 官方域（弱特征即可）、[isTrustedHosting]
 *    可信对象存储/CDN 宿主域（中等门槛）、其它任意域（[STRONG_SCORE] 强特征）。
 * 3. 防钓鱼只看**可注册域**是否被冒充：`deepseek.com.evil.com`、`deepseek-chat.cn`
 *    这类注册域本身就是仿冒品的拒绝；`deepseek-xxx.obs.cn-east-3.myhuaweicloud.com`
 *    的注册域是 `myhuaweicloud.com`（云厂商），bucket 前缀是用户可控子域，不属于冒充。
 */
object ExportLinkParser {

    private val URL_PATTERN = Regex(
        """https?://[^\s"'<>（）()【】\[\]，。；、]+""",
        RegexOption.IGNORE_CASE,
    )

    /** 官方域下命中任一导出特征即可采纳 */
    const val MIN_SCORE = 1

    /** 可信托管域（云存储/CDN）：签名参数或 zip 特征达 2 分即可采纳 */
    const val TRUSTED_SCORE = 2

    /** 陌生域要求更强的导出特征（.zip + 签名 / CDN 子域等） */
    const val STRONG_SCORE = 3

    private const val MAX_LENGTH = 2048

    /**
     * 可信对象存储 / CDN 的**注册域**后缀。
     * 命中说明链接由云厂商托管，bucket 前缀（含 deepseek 字样）不能作为钓鱼依据。
     */
    private val TRUSTED_HOSTING_SUFFIXES = listOf(
        // 华为云 OBS
        "myhuaweicloud.com",
        "huaweicloud.com",
        // 阿里云 OSS / CDN
        "aliyuncs.com",
        "alicdn.com",
        // AWS S3 / CloudFront
        "amazonaws.com",
        "cloudfront.net",
        // 腾讯云 COS / CDN
        "myqcloud.com",
        "tencentcs.com",
        "qcloudcdn.com",
        // 火山引擎
        "volces.com",
        "bytecdntp.com",
        // 七牛 / 又拍
        "qiniucdn.com",
        "qiniudn.com",
        "upyun.com",
        // Azure / GCP / DO / B2 / R2
        "blob.core.windows.net",
        "azureedge.net",
        "googleapis.com",
        "digitaloceanspaces.com",
        "backblazeb2.com",
        "r2.cloudflarestorage.com",
    )

    /** 返回得分最高且可信的候选，没有则为 null */
    fun find(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return candidates(text).maxByOrNull { score(it) }
    }

    /**
     * 手动粘贴用：放宽到「文本里第一个 https 链接」。
     * 用户可能直接从浏览器地址栏复制，此时链接可能不含任何导出关键字。
     */
    fun findLoose(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_PATTERN.findAll(text).mapNotNull { normalize(it.value) }.firstOrNull()
    }

    fun candidates(text: String): List<String> = URL_PATTERN.findAll(text)
        .mapNotNull { normalize(it.value) }
        .distinct()
        .filter { accept(it) }
        .toList()

    /** 是否可以作为导出包下载：官方域看弱特征，可信托管域看中特征，陌生域要强特征 */
    fun accept(url: String): Boolean {
        val host = hostOf(url)
        if (host.isBlank() || isImpersonation(host)) return false
        val value = score(url)
        return when {
            isOfficialHost(host) -> value >= MIN_SCORE
            isTrustedHosting(host) -> value >= TRUSTED_SCORE
            else -> value >= STRONG_SCORE
        }
    }

    /** 下载器复用：确认这条地址值得尝试下载 */
    fun looksDownloadable(url: String): Boolean = accept(url)

    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")

    private fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('.', ',', ';', ':', '。', '，', '；', ')', '）', ']', '】')
        if (trimmed.length > MAX_LENGTH) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        if (isImpersonation(host)) return null
        // 官方链路一律 https；若用户复制到 http 链接则升级，避免明文下载
        return trimmed.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }

    fun isOfficialHost(host: String): Boolean {
        val value = host.lowercase()
        return value == "deepseek.com" || value.endsWith(".deepseek.com")
    }

    /** 云厂商对象存储 / CDN 宿主域：链接可信，但内容仍需下载后校验 ZIP 结构 */
    fun isTrustedHosting(host: String): Boolean {
        val value = host.lowercase()
        return TRUSTED_HOSTING_SUFFIXES.any { value == it || value.endsWith(".$it") }
    }

    /**
     * 可注册域被冒充：`deepseek.com.evil.com`、`deepseek-chat.cn`、`evildeepseek.com`。
     * 注意：bucket 前缀（`deepseek-xxx.obs.cn-east-3.myhuaweicloud.com`）不算冒充，
     * 因为其注册域是云厂商域名。
     */
    fun isImpersonation(host: String): Boolean {
        val value = host.lowercase()
        if (!value.contains("deepseek")) return false
        if (isOfficialHost(value)) return false
        return !isTrustedHosting(value)
    }

    /** 越像「导出包」分越高：.zip > CDN 子域 > export 路径 > download 关键字 > 对象存储签名 */
    fun score(url: String): Int {
        val lower = url.lowercase()
        val host = hostOf(url)
        var score = 0
        if (lower.substringBefore('?').endsWith(".zip")) score += 4
        if (lower.contains(".zip")) score += 2
        if (host.startsWith("cdn.") ||
            host.startsWith("download.") ||
            host.startsWith("export.") ||
            host.startsWith("file.")
        ) {
            score += 3
        }
        if (lower.contains("export")) score += 2
        if (lower.contains("download")) score += 1
        // 对象存储 / CDN 一次性签名：说明这是一条文件下载地址而非普通页面
        if (lower.contains("x-amz-signature") ||
            lower.contains("ossaccesskeyid") ||
            lower.contains("x-obs-") ||
            lower.contains("accesskeyid=") ||
            lower.contains("signature=") ||
            lower.contains("x-oss-signature")
        ) {
            score += 2
        }
        return score
    }
}

/** 识别开放平台 API Key：`sk-` + 至少 16 位字母数字/下划线/连字符 */
object ApiKeyParser {

    private val KEY_PATTERN = Regex("""sk-[A-Za-z0-9_-]{16,}""")

    fun find(text: String?): String? = text?.let { KEY_PATTERN.find(it)?.value }

    fun looksLikeKey(value: String): Boolean = KEY_PATTERN.matches(value.trim())
}
