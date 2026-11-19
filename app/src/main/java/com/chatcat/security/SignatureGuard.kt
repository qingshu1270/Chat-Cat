package com.chatcat.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.chatcat.BuildConfig
import java.security.MessageDigest

/** 应用完整性状态 */
enum class Integrity {
    /** 与官方发布签名一致 */
    OFFICIAL,

    /** debug / beta 包，用调试密钥签名，只允许开发者自测 */
    BETA,

    /** 签名与官方不一致：被二次打包或改签 */
    TAMPERED,

    /** 读不到签名（极端情况） */
    UNKNOWN,
}

/**
 * 签名校验（防盗版）。
 *
 * 原理：读取当前安装包的真实签名证书，与本项目正式发布证书的 SHA-256 比对。
 * 二次打包者必须换签名，因此这一步能挡住「改包塞广告/偷 Key」的分发版本。
 *
 * 校验只依赖 PackageManager，不联网、不上传任何信息。
 */
object SignatureGuard {

    /** 正式发布证书 SHA-256（keystore/chatcat-release.jks，2026-11-19 起） */
    const val OFFICIAL_SHA256 =
        "f618006471d739c6696407d92c00d2ccc291520cf90d2f3e110840b8598394a8"

    /** 读取当前安装包的签名指纹（小写十六进制） */
    fun currentSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun check(context: Context): Integrity {
        val actual = currentSha256(context) ?: return Integrity.UNKNOWN
        return when {
            actual.equals(OFFICIAL_SHA256, ignoreCase = true) -> Integrity.OFFICIAL
            // beta 包用调试密钥签名，属于预期内
            BuildConfig.DEBUG -> Integrity.BETA
            else -> Integrity.TAMPERED
        }
    }

    /** 敏感操作前置校验：签名异常时返回 false，由调用方提示用户 */
    fun allowSensitive(context: Context): Boolean = check(context) != Integrity.TAMPERED
}

/** 需要过签名校验的敏感操作，便于日志与提示文案统一 */
enum class SensitiveAction(val label: String) {
    IMPORT_DATA("导入历史数据"),
    RECHARGE("充值"),
    SAVE_API_KEY("填写 API Key"),
    SENSITIVE_PERMISSION("申请敏感权限"),
}
