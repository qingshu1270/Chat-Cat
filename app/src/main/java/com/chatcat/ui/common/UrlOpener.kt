package com.chatcat.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler

/** 只在 http/https 下打开；优先 Custom Tabs，失败回退系统浏览器 */
fun openUrl(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

fun customTabUriHandler(context: Context): UriHandler = object : UriHandler {
    override fun openUri(uri: String) = openUrl(context, uri)
}
