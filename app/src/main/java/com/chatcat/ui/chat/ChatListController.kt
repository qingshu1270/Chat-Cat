package com.chatcat.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 对话列表的滚动控制。
 *
 * 关键决定：列表用 `reverseLayout = true`（item 0 在最底部），消息按「新→旧」喂进去。
 * 这样「底部」就是布局的锚点，而不是需要不断追上去的目标：
 *  - 进会话天然停在最新一条，不存在「先渲在顶部再滚下来」；
 *  - 流式增长时新行从底部往上顶，不需要每帧补一次滚动，换行不会二次位移；
 *  - 键盘弹起时视口变矮，锚点仍在底部，内容与输入框同步上移。
 *
 * 之前那套「settle + 缓动追底 + ime 补偿 + 跟随开关」正是因为没有锚点才需要，
 * 现在只保留「回到最新」这一个动作。
 */
@Stable
class ChatListController internal constructor(
    val listState: LazyListState,
) {
    /** 是否停在最新一条：reverseLayout 下即落在第 0 项、偏移 0 */
    val atLatest: Boolean
        get() = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0

    /** 回到最新一条（发送后、点「回到底部」） */
    suspend fun scrollToLatest(animated: Boolean = true) {
        if (animated) {
            listState.animateScrollToItem(0)
        } else {
            listState.scrollToItem(0)
        }
    }
}

/**
 * @param sessionKey 会话标识：换会话就重开一份滚动状态，避免带着上一个会话的位置
 */
@Composable
fun rememberChatListController(sessionKey: String): ChatListController {
    // 初始位置就是 index 0 = 底部，不需要额外的「对位」步骤
    val listState = rememberSaveable(sessionKey, saver = LazyListState.Saver) {
        LazyListState()
    }
    return remember(listState) { ChatListController(listState) }
}
