package com.chatcat.ui.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 思维链限高区高度（开发者模式可调）：只在「思考中・限高」时生效 */
val LocalThinkingPanelHeight = staticCompositionLocalOf { 168.dp }

/**
 * 对话页与渲染预览页共用的页面骨架。
 *
 * 之前两个页面各写一套 Scaffold / Column，键盘内边距也就各有一套规则，
 * 结果从预览页返回后行为会和对话页不一致。统一到这里之后，
 * 「键盘高度只由 imePadding 消化」这条规则只需要维护一份。
 */
@Composable
fun ChatChrome(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content,
    )
}
