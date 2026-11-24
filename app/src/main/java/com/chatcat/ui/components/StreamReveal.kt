package com.chatcat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.ceil

/**
 * 字符级揭示（Reveal Buffer）。
 *
 * 之前是「每来一坨文本就把全文画上去」：Flash 一次投递几十到几百字符，
 * 于是视觉上是「蹦」出来一段，而不是「流」出来。节流频率治不了这个——
 * 「蹦」的量取决于一次画多少字。
 *
 * 这里把「到达」和「显示」拆开：
 * - 目标 [text] 可以突然变长很多；
 * - 真正渲染的是它的前缀，长度 `shown` 每帧推进一小步（[DRAIN_FRAMES] 帧内追平积压）。
 * 于是无论网络一次来 20 字还是 300 字，画面都是每帧几个字地长出来。
 *
 * 结束后（streaming=false）直接返回全文，不做动画——历史消息不该在打开时重播。
 */
/** 揭示节奏（开发者模式可调）：单帧上限与追平帧数 */
data class StreamTuning(val maxStep: Int = 6, val drainFrames: Int = 20)

/** 由 App 根按设置注入，默认值即线上调好的手感 */
val LocalStreamTuning = staticCompositionLocalOf { StreamTuning() }

@Composable
internal fun rememberRevealedText(text: String, streaming: Boolean): String {
    val tuning = LocalStreamTuning.current
    // 历史消息首次组合时就当作「已全部显示」，不做动画；流式消息从 0 开始逐字揭示。
    // （中途续写的情况：shown 已是旧长度，新追加的部分照常揭示。）
    var shown by remember { mutableIntStateOf(if (streaming) 0 else text.length) }
    val target = rememberUpdatedState(text)
    val live = rememberUpdatedState(streaming)
    val active = streaming || shown < text.length

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val full = target.value
            if (shown > full.length) shown = full.length          // 文本被整体替换时回退
            val backlog = full.length - shown
            if (backlog <= 0) {
                if (!live.value) break                             // 已结束且追平：收工
                continue
            }
            // 流式中按 DRAIN_FRAMES 追平（约 300ms）；结束后用 TAIL_FRAMES 收尾（约 100ms），
            // 避免"结束了还慢慢打字"。单帧上限 MAX_STEP，防止又变成一帧一大片。
            val frames = if (live.value) tuning.drainFrames else (tuning.drainFrames / 3).coerceAtLeast(4)
            val step = ceil(backlog / frames.toFloat()).toInt().coerceIn(1, tuning.maxStep)
            shown = (shown + step).coerceAtMost(full.length)
        }
    }

    return if (shown >= text.length) text else text.substring(0, shown.coerceIn(0, text.length))
}


/**
 * 尾巴上未闭合的 Markdown 语法临时补齐，避免「先露符号再跳变」。
 *
 * 只处理最常见的几种（代码围栏、粗体、行内代码、删除线）；
 * 补齐只是**渲染投影**，不影响原文——真正的闭合符到达后，这里自然不再追加。
 */
internal fun closeOpenMarkdown(markdown: String): String {
    if (markdown.isEmpty()) return markdown
    // 代码围栏优先：三个反引号必须成对，否则后面的内容会被整段当代码
    if (countOf(markdown, "```") % 2 == 1) return markdown + "\n```"

    val sb = StringBuilder(markdown)
    // 去掉围栏后再数单个反引号，避免把 ``` 里的三个数成"奇偶抵消"
    val withoutFence = markdown.replace("```", "")
    if (countOf(withoutFence, "`") % 2 == 1) sb.append('`')
    if (countOf(markdown, "**") % 2 == 1) sb.append("**")
    if (countOf(markdown, "~~") % 2 == 1) sb.append("~~")
    return sb.toString()
}

private fun countOf(text: String, token: String): Int {
    var from = 0
    var n = 0
    while (true) {
        val at = text.indexOf(token, from)
        if (at < 0) return n
        n++
        from = at + token.length
    }
}
