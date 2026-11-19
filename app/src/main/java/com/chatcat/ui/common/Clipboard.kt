package com.chatcat.ui.common

import android.content.ClipboardManager
import android.content.Context

/** 剪贴板读取结果：区分「空」与「系统拒绝」，后者用于走降级引导 */
sealed interface ClipboardText {
    data class Text(val value: String) : ClipboardText
    data object Empty : ClipboardText
    data object Blocked : ClipboardText
}

/**
 * 读取剪贴板首条文本。
 *
 * Android 10+ 只允许**拥有输入焦点的前台应用**读取剪贴板，未获得焦点时
 * `primaryClip` 返回 null（与「剪贴板为空」不可区分）甚至抛 SecurityException，
 * 因此调用方必须在回到前台后再延迟重试，本函数只负责安全读取、不阻塞。
 */
fun readClipboardText(context: Context): ClipboardText {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return ClipboardText.Blocked
    return try {
        val clip = manager.primaryClip ?: return ClipboardText.Empty
        if (clip.itemCount == 0) return ClipboardText.Empty
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        if (text.isNullOrBlank()) ClipboardText.Empty else ClipboardText.Text(text)
    } catch (e: SecurityException) {
        ClipboardText.Blocked
    } catch (e: Exception) {
        ClipboardText.Blocked
    }
}

/** 剪贴板原文（未识别成功时用于「手动粘贴」预填，失败返回 null） */
fun readClipboardRaw(context: Context): String? =
    (readClipboardText(context) as? ClipboardText.Text)?.value

/** 诊断用短摘要：只取开头，便于在界面上告诉用户「读到的是什么」 */
fun clipboardPreview(raw: String?, max: Int = 48): String {
    val text = raw?.trim().orEmpty().replace('\n', ' ')
    if (text.isEmpty()) return ""
    return if (text.length <= max) text else text.take(max) + "…"
}
