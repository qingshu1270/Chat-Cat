package com.chatcat.ui.common

import android.content.Context
import android.content.Intent

/** DeepSeek 官方入口：App 包名与开放平台链接集中在此，便于一处维护 */
object OfficialLinks {

    /** 官方 Android App 包名（docs/01 静态分析确认） */
    const val PACKAGE = "com.deepseek.chat"

    /** 开放平台：充值 */
    const val RECHARGE = "https://platform.deepseek.com/top_up"

    /** 开放平台：API Keys 管理 */
    const val API_KEYS = "https://platform.deepseek.com/api_keys"

    /** 开放平台：用量 */
    const val USAGE = "https://platform.deepseek.com/usage"

    /** 开放平台首页 */
    const val CONSOLE = "https://platform.deepseek.com"

    /** 官方网页版对话 */
    const val WEB_CHAT = "https://chat.deepseek.com"

    /** 官方站点（App 下载入口） */
    const val HOME = "https://www.deepseek.com"
}

/** 链式启动官方 App；未安装返回 false，由 UI 走降级提示 */
fun openOfficialApp(context: Context): Boolean {
    val intent = context.packageManager
        .getLaunchIntentForPackage(OfficialLinks.PACKAGE)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ?: return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

fun isOfficialAppInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getLaunchIntentForPackage(OfficialLinks.PACKAGE) != null
}.getOrDefault(false)
