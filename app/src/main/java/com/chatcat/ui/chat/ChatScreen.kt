package com.chatcat.ui.chat

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import com.chatcat.R
import kotlin.random.Random
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.data.prefs.AppSettings
import com.chatcat.domain.model.formatCount
import com.chatcat.domain.model.UsagePricing
import com.chatcat.domain.model.UsageSummary
import com.chatcat.domain.model.AttachmentPolicy
import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.ContinuePolicy
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.SessionSummary
import com.chatcat.domain.model.ToolParser
import com.chatcat.domain.model.ToolSource
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.UiMessage
import com.chatcat.ui.components.AttachmentImage
import com.chatcat.ui.components.MarkdownText
import com.chatcat.ui.components.rememberUriImage
import com.chatcat.ui.common.PeakValleyTag
import com.chatcat.ui.common.customTabUriHandler
import com.chatcat.ui.components.SecondaryPage
import com.chatcat.ui.theme.LocalDarkTheme
import com.chatcat.ui.theme.Tokens
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ChatPage {
    data class Model(val current: String) : ChatPage
    data class SelectText(val text: String) : ChatPage
    data class Rename(val session: SessionSummary) : ChatPage
    data class DeleteSession(val session: SessionSummary) : ChatPage
    data class DeleteMany(val ids: Set<String>, val count: Int) : ChatPage
    data class ImagePreview(val uri: String) : ChatPage

    /** 搜索结果页（页中页） */
    data class SearchResults(val sources: List<ToolSource>) : ChatPage

    /** 已浏览页面列表（点「已浏览 N 个页面」胶囊打开，不能直接跳某一个网页） */
    data class BrowsedPages(val pages: List<ToolSource>) : ChatPage

    /** 应用内浏览器 */
    data class Browser(val url: String) : ChatPage
}

/** 聊天页内的导航（搜索结果页 / 内置浏览器），供深层组件直接调用 */
private class ChatNavigator(
    val openSearch: (List<ToolSource>) -> Unit,
    val openBrowse: (List<ToolSource>) -> Unit,
    val openUrl: (String) -> Unit,
)

private val LocalChatNavigator = staticCompositionLocalOf<ChatNavigator?> { null }

private object AppModel {
    const val FLASH = "deepseek-flash"
    const val PRO = "deepseek-v4-pro"
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSelectSession: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onPinSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onPinSessions: (Set<String>, Boolean) -> Unit,
    onDeleteSessions: (Set<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
    onFeedback: (String, Int) -> Unit,
    onShowUsage: (String) -> Unit,
    onDismissUsage: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onRequestModel: (String) -> Unit,
    onConfirmModel: () -> Unit,
    onDismissModel: () -> Unit,
    onConsumeToast: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApiSetup: () -> Unit,
    onDismissIntegrityAlert: () -> Unit,
    onOpenBalance: () -> Unit = {},
    openDrawerOnStart: Boolean = false,
    onDrawerOpened: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = state.sessions.firstOrNull { it.id == state.sessionId }?.title.orEmpty()
    var page by remember { mutableStateOf<ChatPage?>(null) }
    val navigator = remember {
        ChatNavigator(
            openSearch = { sources -> page = ChatPage.SearchResults(sources) },
            openBrowse = { pages -> page = ChatPage.BrowsedPages(pages) },
            openUrl = { url -> page = ChatPage.Browser(url) },
        )
    }
    var editTarget by remember { mutableStateOf<UiMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onAddAttachments(uris) }

    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (saved && uri != null) onAddAttachments(listOf(uri))
    }
    // 拍照结果落到 cache/images，经 FileProvider 授权给相机应用写入
    val takePhoto = {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(openDrawerOnStart) {
        if (openDrawerOnStart) {
            drawerState.open()
            onDrawerOpened()
        }
    }

    LaunchedEffect(state.toast) {
        val text = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        onConsumeToast()
    }

    // 盗版/改包提示：敏感操作被拦下时弹出
    if (state.integrityAlert != null) {
        AlertDialog(
            onDismissRequest = onDismissIntegrityAlert,
            title = { Text("应用签名异常") },
            text = {
                Column {
                    val noMascot = remember {
                        val pool = intArrayOf(
                            R.drawable.integrity_no_1, R.drawable.integrity_no_2,
                            R.drawable.integrity_no_3, R.drawable.integrity_no_4,
                            R.drawable.integrity_no_5, R.drawable.integrity_no_6,
                            R.drawable.integrity_no_7, R.drawable.integrity_no_8,
                            R.drawable.integrity_no_9,
                        )
                        pool[kotlin.random.Random.nextInt(pool.size)]
                    }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(noMascot),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(96.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("当前安装包的签名与官方发布版本不一致，可能被二次打包或改签。")
                    Spacer(Modifier.height(8.dp))
                    Text("为了保护你的 API Key 与聊天数据，「${state.integrityAlert}」已暂停。")
                    Spacer(Modifier.height(8.dp))
                    Text("请前往 GitHub 或 Gitee 搜索「Chat Cat」，下载免费正版安装包。")
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissIntegrityAlert) { Text("我知道了") }
            },
        )
    }

    // 正文/来源里的链接统一进应用内浏览器（页中页），不跳出应用
    val internalUriHandler = remember(navigator) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) = navigator.openUrl(uri)
        }
    }
    CompositionLocalProvider(
        LocalUriHandler provides internalUriHandler,
        LocalChatNavigator provides navigator,
    ) {
    Box(Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                sessions = state.sessions,
                avatarUri = state.avatarUri,
                avatarVersion = state.avatarVersion,
                currentId = state.sessionId,
                onSelect = {
                    onSelectSession(it)
                    scope.launch { drawerState.close() }
                },
                onPin = onPinSession,
                onPinMany = onPinSessions,
                onRenameRequest = { session ->
                    page = ChatPage.Rename(session)
                    scope.launch { drawerState.close() }
                },
                onDeleteRequest = { session ->
                    page = ChatPage.DeleteSession(session)
                    scope.launch { drawerState.close() }
                },
                onBatchDeleteRequest = { ids ->
                    page = ChatPage.DeleteMany(ids, ids.size)
                    scope.launch { drawerState.close() }
                },
                displayName = state.settings.displayName,
                onOpenSettings = {
                    onOpenSettings()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ChatTopBar(
                    title = if (state.visibleMessages.isEmpty()) "" else title,
                    model = state.currentModel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSwitchModel = { page = ChatPage.Model(state.currentModel) },
                    onNewChat = onNewChat,
                )
            },
            bottomBar = {
                val target = editTarget
                if (target != null) {
                    EditComposer(
                        text = editText,
                        onTextChange = { editText = it },
                        thinking = state.thinking,
                        search = state.search,
                        onToggleThinking = onToggleThinking,
                        onToggleSearch = onToggleSearch,
                        onClose = {
                            editTarget = null
                            editText = ""
                        },
                        onSubmit = {
                            if (editText.isNotBlank()) {
                                val id = target.id
                                val value = editText
                                editTarget = null
                                editText = ""
                                onEditMessage(id, value)
                            }
                        },
                    )
                } else {
                    Composer(
                        showDisclaimer = state.visibleMessages.isNotEmpty(),
                        input = state.input,
                        streaming = state.streaming,
                        thinking = state.thinking,
                        search = state.search,
                        attachments = state.attachments,
                        balanceYuan = state.balanceYuan,
                        balanceWarn = state.settings.balanceWarn,
                        onOpenBalance = onOpenBalance,
                        onInput = onInput,
                        onSend = onSend,
                        onStop = onStop,
                        onToggleThinking = onToggleThinking,
                        onToggleSearch = onToggleSearch,
                        onAddFiles = { filePicker.launch(arrayOf("*/*")) },
                        onTakePhoto = takePhoto,
                        onRemoveAttachment = onRemoveAttachment,
                        onRetryAttachment = onRetryAttachment,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            val messages = state.visibleMessages
            val canScrollDown by remember {
                derivedStateOf { listState.canScrollForward }
            }
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            // 用户上滑查看历史时停止自动跟随；回到底部或发送后恢复
            var follow by remember { mutableStateOf(true) }

            LaunchedEffect(listState) {
                // 手指一拖动就停止跟随，松手时若已在底部再恢复
                listState.interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is DragInteraction.Start -> follow = false
                        is DragInteraction.Stop, is DragInteraction.Cancel ->
                            follow = !listState.canScrollForward
                        else -> Unit
                    }
                }
            }
            LaunchedEffect(state.streaming) {
                if (state.streaming) follow = true
            }
            LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
                if (messages.isNotEmpty() && follow) {
                    listState.scrollToBottom(messages.lastIndex)
                }
            }

            if (messages.isEmpty()) {
                EmptyState(
                    modifier = Modifier.padding(padding),
                    hasKey = state.hasKey,
                    onOpenApiSetup = onOpenApiSetup,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (!state.hasKey) {
                            item(key = "api-prompt") {
                                ApiPromptBanner(onOpenApiSetup = onOpenApiSetup)
                            }
                        }
                        items(messages, key = { it.id }) { message ->
                            MessageItem(
                                message = message,
                                thinkingDisplay = state.settings.thinkingDisplay,
                                feedback = state.feedback[message.id] ?: 0,
                                onRetry = onRetry,
                                onRegenerate = onRegenerate,
                                isLast = message.id == messages.lastOrNull()?.id,
                                onContinue = onContinue,
                                onSwitchVersion = onSwitchVersion,
                                onFeedback = onFeedback,
                                onShowUsage = onShowUsage,
                                onSelectText = { page = ChatPage.SelectText(it.text) },
                                onEdit = { message ->
                                    editTarget = message
                                    editText = message.text
                                },
                                onPreviewImage = { uri -> page = ChatPage.ImagePreview(uri) },
                            )
                        }
                    }
                    if (canScrollDown) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                )
                                .clickable {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    follow = true
                                    scope.launch {
                                        listState.scrollToBottom(messages.lastIndex)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "转至底部",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
                if (editTarget != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.66f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
            }
        }
    }

        page?.let { current ->
            when (current) {
                is ChatPage.Model -> ModelPage(
                    current = state.currentModel,
                    onSelect = { model ->
                        if (model != state.currentModel) {
                            onRequestModel(model)
                            onConfirmModel()
                        }
                        page = null
                    },
                    onBack = {
                        onDismissModel()
                        page = null
                    },
                )

                is ChatPage.SelectText -> SelectTextPage(
                    text = current.text,
                    onBack = { page = null },
                )

                is ChatPage.Rename -> RenameSessionPage(
                    session = current.session,
                    onSave = { title ->
                        onRenameSession(current.session.id, title)
                        page = null
                    },
                    onBack = { page = null },
                )

                is ChatPage.DeleteSession -> DeleteSessionPage(
                    session = current.session,
                    onConfirm = {
                        onDeleteSession(current.session.id)
                        page = null
                    },
                    onBack = { page = null },
                )

                is ChatPage.DeleteMany -> DeleteManyPage(
                    count = current.count,
                    onConfirm = {
                        onDeleteSessions(current.ids)
                        page = null
                    },
                    onBack = { page = null },
                )

                is ChatPage.ImagePreview -> ImagePreviewPage(
                    uri = current.uri,
                    onBack = { page = null },
                )

                is ChatPage.SearchResults -> SearchResultsPage(
                    sources = current.sources,
                    onOpen = { url -> page = ChatPage.Browser(url) },
                    onBack = { page = null },
                )

                is ChatPage.BrowsedPages -> SearchResultsPage(
                    sources = current.pages,
                    title = "已浏览搜索结果",
                    onOpen = { url -> page = ChatPage.Browser(url) },
                    onBack = { page = null },
                )

                is ChatPage.Browser -> com.chatcat.ui.browser.BrowserPage(
                    url = current.url,
                    onClose = { page = null },
                )
            }
        }

        // 用量页由 ViewModel 状态驱动（需异步查库）
        if (state.usageMessageId != null && state.usageDetail != null) {
            UsagePage(detail = state.usageDetail!!, onBack = onDismissUsage)
        }
    }
    }
}

@Composable
private fun UsagePage(detail: UsageDetail, onBack: () -> Unit) {
    val peak = remember { UsagePricing.isPeak(java.util.Calendar.getInstance()) }
    SecondaryPage(title = "用量", onBack = onBack) {
        Spacer(Modifier.height(18.dp))
        UsageCard(title = "本次", usage = detail.thisMessage, peak = peak)
        Spacer(Modifier.height(16.dp))
        UsageCard(title = "本月累计", usage = detail.thisMonth, peak = peak)
        Spacer(Modifier.height(16.dp))
        Text(
            "按公开价目表估算，实际以账单为准。缓存命中按优惠价计。",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UsageCard(title: String, usage: UsageSummary, peak: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (LocalDarkTheme.current) Tokens.BubbleDark else Tokens.BgLightSecondary)
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        UsageLine("输入", formatCount(usage.totalInput))
        UsageLine("　其中缓存命中", formatCount(usage.cacheReadTokens))
        UsageLine("输出", formatCount(usage.outputTokens))
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        val yuan = UsagePricing.estimateYuan(usage, peak)
        Text(
            "估算：¥${String.format("%.3f", yuan)}${if (peak) "（高峰时段）" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 设置-用量统计页：本月累计。供 SettingsScreen 复用，保持同一套卡片与文案。 */
@Composable
fun UsageOverviewPage(usage: UsageSummary, onBack: () -> Unit) {
    val peak = remember { UsagePricing.isPeak(java.util.Calendar.getInstance()) }
    SecondaryPage(title = "用量统计", onBack = onBack) {
        Spacer(Modifier.height(18.dp))
        UsageCard(title = "本月累计", usage = usage, peak = peak)
        Spacer(Modifier.height(16.dp))
        Text(
            "按公开价目表估算，实际以账单为准。缓存命中按优惠价计。",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChatTopBar(
    title: String,
    model: String,
    onMenu: () -> Unit,
    onSwitchModel: () -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Menu,
                contentDescription = "会话列表",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSwitchModel),
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (model == AppModel.PRO) "V4 Pro" else "V4.1",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "切换模型",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNewChat, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.AddCircleOutline,
                contentDescription = "开启新对话",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DrawerContent(
    sessions: List<SessionSummary>,
    avatarUri: String?,
    avatarVersion: Long,
    currentId: String?,
    onSelect: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onPinMany: (Set<String>, Boolean) -> Unit,
    onRenameRequest: (SessionSummary) -> Unit,
    onDeleteRequest: (SessionSummary) -> Unit,
    onBatchDeleteRequest: (Set<String>) -> Unit,
    displayName: String,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.preview.contains(query, ignoreCase = true)
            }
        }
    }
    val groups = remember(filtered) { buildGroups(filtered) }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.82f),
        drawerContainerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选 ${selected.size} 组",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    )
                    TextButton(onClick = {
                        selecting = false
                        selected = emptySet()
                    }) { Text("取消") }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索对话内容...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "暂无对话" else "没有找到相关内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "header-${group.label}") {
                            Text(
                                text = group.label,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(group.items, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                selected = session.id == currentId,
                                selecting = selecting,
                                checked = session.id in selected,
                                onClick = {
                                    if (selecting) {
                                        selected = toggleSelected(selected, session.id)
                                    } else {
                                        onSelect(session.id)
                                    }
                                },
                                onLongClick = {
                                    if (selecting) selected = toggleSelected(selected, session.id)
                                },
                                onRename = { onRenameRequest(session) },
                                onPin = { onPin(session.id, !session.pinned) },
                                onMultiSelect = {
                                    selecting = true
                                    selected = setOf(session.id)
                                },
                                onDelete = { onDeleteRequest(session) },
                            )
                        }
                        if (group.label == "置顶") {
                            item(key = "divider-pinned") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }

            if (selecting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onPinMany(selected, true)
                            selecting = false
                            selected = emptySet()
                        },
                        enabled = selected.isNotEmpty(),
                    ) { Text("置顶") }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (selected.isNotEmpty()) {
                                val ids = selected
                                selecting = false
                                selected = emptySet()
                                onBatchDeleteRequest(ids)
                            }
                        },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatar = com.chatcat.ui.components.rememberUriImage(avatarUri, 96, avatarVersion)
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Tokens.PillBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatar != null) {
                            androidx.compose.foundation.Image(
                                bitmap = avatar,
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                "C",
                                style = MaterialTheme.typography.labelSmall,
                                color = Tokens.PillText,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            displayName.ifBlank { "Chat Cat" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "第三方客户端",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    selected: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onMultiSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val highlight = selected || checked

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) {
                    if (LocalDarkTheme.current) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Tokens.BubbleLight
                    }
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!selecting) menu = true
                    onLongClick()
                },
            )
            .padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = session.title.ifBlank { "新对话" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (selected && !selecting) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PopupMenuItem("重命名", Icons.Outlined.Edit) {
                    menu = false
                    onRename()
                }
                MenuDivider()
                PopupMenuItem(
                    if (session.pinned) "取消置顶" else "置顶",
                    Icons.Outlined.PushPin,
                ) {
                    menu = false
                    onPin()
                }
                MenuDivider()
                PopupMenuItem("多选", Icons.Outlined.Checklist) {
                    menu = false
                    onMultiSelect()
                }
                MenuDivider()
                PopupMenuItem("删除", Icons.Outlined.Delete, destructive = true) {
                    menu = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(width),
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun PopupMenuItem(
    text: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (destructive) Color(0xFFE5484D) else Color(0xFF555555),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = if (destructive) Color(0xFFE5484D) else Color(0xFF1A1A1A),
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEFEFEF))
}

private fun toggleSelected(current: Set<String>, id: String): Set<String> =
    if (id in current) current - id else current + id

/** 无 Key 时的持久提示条：提示 + 小狮子 logo，点击 logo 进入 API 配置 */
/** 日间吉祥物 9 张，新建对话时随机取一张（branding：小狮子图集） */
private val MASCOTS = intArrayOf(
    R.drawable.mascot_day_1, R.drawable.mascot_day_2, R.drawable.mascot_day_3,
    R.drawable.mascot_day_4, R.drawable.mascot_day_5, R.drawable.mascot_day_6,
    R.drawable.mascot_day_7, R.drawable.mascot_day_8, R.drawable.mascot_day_9,
)

/** 夜间时段独享的 3 张「睡觉」小狮子，白天不会出现 */
private val MASCOTS_NIGHT = intArrayOf(
    R.drawable.mascot_night_1, R.drawable.mascot_night_2, R.drawable.mascot_night_3,
)

/** 22:00–06:00 视为夜间 */
private fun isNightNow(): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return hour >= 22 || hour < 6
}

/** 按当前时段取表情池 */
private fun mascotPool(night: Boolean): IntArray = if (night) MASCOTS_NIGHT else MASCOTS

@Composable
private fun ApiPromptBanner(onOpenApiSetup: () -> Unit) {
    val pool = remember { mascotPool(isNightNow()) }
    val mascot = remember(pool) { pool[Random.nextInt(pool.size)] }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (LocalDarkTheme.current) Tokens.BubbleDark else Tokens.BgLightSecondary)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(mascot),
            contentDescription = "配置 API",
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenApiSetup),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "尚未接入 API",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "点按小狮子配置 API Key 后开始对话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    hasKey: Boolean = true,
    onOpenApiSetup: () -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 每次进入空白对话随机取一张小狮子；夜间只从「睡觉」组里取
        val night = remember { isNightNow() }
        val mascot = remember(night) {
            val pool = mascotPool(night)
            pool[Random.nextInt(pool.size)]
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!hasKey) {
                // 吉祥物 logo：点击进入 API 配置
                Image(
                    painter = painterResource(mascot),
                    contentDescription = "配置 API",
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenApiSetup),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "尚未接入 API",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "点按上方小狮子配置 API Key 后开始对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 有 Key 的空白对话：中央随机小狮子代替原蓝色气泡
                Image(
                    painter = painterResource(mascot),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "今天有什么可以帮到你？",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (night) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "夜深了早些歇息吧~",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: UiMessage,
    thinkingDisplay: String,
    feedback: Int,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    isLast: Boolean,
    onContinue: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
    onFeedback: (String, Int) -> Unit,
    onShowUsage: (String) -> Unit,
    onSelectText: (UiMessage) -> Unit,
    onEdit: (UiMessage) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    when (message.role) {
        Role.USER -> {
            var menu by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box {
                    Column(
                        // 正文保护：只有长按才唤起菜单，点击/拖动不会与正文产生交互
                        modifier = Modifier.pointerInput(message.id) {
                            detectTapGestures(onLongPress = { menu = true })
                        },
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (message.attachments.isNotEmpty()) {
                            UserAttachments(
                                attachments = message.attachments,
                                onPreviewImage = onPreviewImage,
                            )
                            if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                        }
                        if (message.text.isNotBlank()) {
                            Surface(
                                color = if (LocalDarkTheme.current) Tokens.BubbleDark else Tokens.BubbleLight,
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(
                                    text = message.text,
                                    modifier = Modifier
                                        .widthIn(max = 300.dp)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                )
                            }
                        }
                        // 「修改」产生的多个提问版本，可在原地切回
                        if (message.versionCount > 1) {
                            Spacer(Modifier.height(2.dp))
                            VersionSwitcher(message, onSwitchVersion)
                        }
                    }
                    AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        PopupMenuItem("复制", Icons.Outlined.ContentCopy) {
                            menu = false
                            clipboard.setText(AnnotatedString(message.text))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                        MenuDivider()
                        PopupMenuItem("选择文本", Icons.Outlined.SelectAll) {
                            menu = false
                            onSelectText(message)
                        }
                        MenuDivider()
                        PopupMenuItem("修改", Icons.Outlined.Edit) {
                            menu = false
                            onEdit(message)
                        }
                        MenuDivider()
                        PopupMenuItem("分享", Icons.Outlined.Share) {
                            menu = false
                            shareText(context, message.text)
                        }
                    }
                }
            }
        }

        Role.ASSISTANT -> {
            var menu by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            Box {
                Column(
                    // 正文保护：默认不可选中、不可点链接，长按只负责唤起菜单
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onLongPress = { if (!message.isStreaming) menu = true },
                            )
                        },
                ) {
            val thinkingActive = message.isStreaming && message.blocks
                .filter { it.isThinking }
                .let { list -> list.isEmpty() || list.any { !it.closed } }
            if (message.thinking.isNotBlank() || thinkingActive) {
                ThinkingRow(message, thinkingActive, thinkingDisplay)
                Spacer(Modifier.height(6.dp))
            }
            if (message.text.isNotBlank()) {
                // 正文链接可以点（官方就是这样），选中/拖动仍然受保护；
                // 点击由页面级 LocalUriHandler 走内置 Custom Tabs
                MarkdownText(
                    message.textWithCitations,
                    linksClickable = true,
                    sources = message.sources,
                )
            }
            if (!message.isStreaming && message.sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SourceSummary(message.sources)
            }
            when {
                message.status == MessageStatus.FAILED -> {
                    Spacer(Modifier.height(10.dp))
                    ErrorCard(message.error.orEmpty(), onRetry)
                }
                message.status == MessageStatus.REFUSED -> {
                    Spacer(Modifier.height(10.dp))
                    NoticeCard(
                        "这个话题我不太方便展开，我们聊点别的吧。\n" +
                            "换个问法或换个方向，我会尽力帮你。",
                    )
                }
            }
            if (message.text.isNotBlank() && !message.isStreaming) {
                Spacer(Modifier.height(6.dp))
                ReplyActions(
                    message = message,
                    feedback = feedback,
                    onRegenerate = onRegenerate,
                    onSwitchVersion = onSwitchVersion,
                    onFeedback = onFeedback,
                    onShowUsage = onShowUsage,
                )
                if (isLast && ContinuePolicy.canContinue(message)) {
                    Spacer(Modifier.height(8.dp))
                    ContinueButton(onContinue)
                }
            }
                }
                AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PopupMenuItem("复制", Icons.Outlined.ContentCopy) {
                        menu = false
                        clipboard.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    MenuDivider()
                    PopupMenuItem("选择文本", Icons.Outlined.SelectAll) {
                        menu = false
                        onSelectText(message)
                    }
                    MenuDivider()
                    PopupMenuItem("重新生成", Icons.Outlined.Refresh) {
                        menu = false
                        onRegenerate()
                    }
                    MenuDivider()
                    PopupMenuItem("喜欢", Icons.Outlined.ThumbUp) {
                        menu = false
                        onFeedback(message.id, 1)
                    }
                    MenuDivider()
                    PopupMenuItem("不喜欢", Icons.Outlined.ThumbDown) {
                        menu = false
                        onFeedback(message.id, -1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSummary(sources: List<ToolSource>) {
    val nav = LocalChatNavigator.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { nav?.openSearch(sources) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Public,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "${sources.size} 个网页",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "继续生成",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplyActions(
    message: UiMessage,
    feedback: Int,
    onRegenerate: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
    onFeedback: (String, Int) -> Unit,
    onShowUsage: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.hasVersions) {
            VersionSwitcher(message, onSwitchVersion)
            Spacer(Modifier.width(10.dp))
        }
        ActionIcon(
            description = "复制",
            onClick = {
                clipboard.setText(AnnotatedString(message.text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
        ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionIcon(
            description = "有帮助",
            onClick = { onFeedback(message.id, 1) },
        ) {
            Icon(
                if (feedback == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = "有帮助",
                modifier = Modifier.size(17.dp),
                tint = if (feedback == 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        ActionIcon(
            description = "没帮助",
            onClick = { onFeedback(message.id, -1) },
        ) {
            Icon(
                if (feedback == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = "没帮助",
                modifier = Modifier.size(17.dp),
                tint = if (feedback == -1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        ActionIcon(description = "重新生成", onClick = onRegenerate) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "重新生成",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionIcon(description = "用量", onClick = { onShowUsage(message.id) }) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "用量",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionIcon(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 官方 `‹ 2/3 ›`：同一提问的多个回答版本 */
@Composable
private fun VersionSwitcher(message: UiMessage, onSwitchVersion: (String, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val canPrev = message.versionIndex > 1
        val canNext = message.versionIndex < message.versionCount
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(enabled = canPrev) { onSwitchVersion(message.id, -1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = "上一版",
                modifier = Modifier.size(16.dp),
                tint = if (canPrev) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        Text(
            text = "${message.versionIndex}/${message.versionCount}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(enabled = canNext) { onSwitchVersion(message.id, 1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "下一版",
                modifier = Modifier.size(16.dp),
                tint = if (canNext) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}

@Composable
private fun ThinkingRow(message: UiMessage, thinkingActive: Boolean, thinkingDisplay: String) {
    // 用户手动点过就以手动为准；没点过则按模式自动决定（chat-demo 的基本逻辑）
    var userExpanded by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    var tick by remember { mutableLongStateOf(0L) }

    // 思考中：除「默认收起」外都展开；结束后：除「默认展开」外都收起。
    // 一旦用户手动展开/收起，就固定住，不再被自动规则覆盖（这正是旧实现会「弹回收起」的原因）。
    val autoExpanded = if (thinkingActive) {
        thinkingDisplay != AppSettings.THINKING_COLLAPSED
    } else {
        thinkingDisplay == AppSettings.THINKING_FULL
    }
    val expanded = userExpanded ?: autoExpanded

    val scroll = rememberScrollState()
    // 思考进行中始终是限高滚动；「默认展开」模式下不限高
    val limited = thinkingActive && thinkingDisplay != AppSettings.THINKING_FULL

    LaunchedEffect(thinkingActive) {
        if (thinkingActive) {
            while (true) {
                tick = System.currentTimeMillis()
                delay(500)
            }
        }
    }

    // 只按内容长度分档触发，避免每个 token 都 animate 造成的掉帧
    LaunchedEffect(message.thinking.length / 48, message.blocks.size, limited) {
        if (limited && expanded) scroll.scrollTo(scroll.maxValue)
    }

    val seconds = when {
        message.thinkingMs > 0 -> (message.thinkingMs / 1000).coerceAtLeast(1)
        message.createdAt <= 0 -> 1L
        thinkingActive -> {
            val now = tick.takeIf { it > 0 } ?: System.currentTimeMillis()
            ((now - message.createdAt) / 1000).coerceAtLeast(1)
        }
        else -> 1L
    }

    val hasThinking = message.thinking.isNotBlank()
    val hasTools = message.blocks.any { it.type == BlockType.TOOL }

    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { userExpanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (thinkingActive) "正在思考" else "已思考（用时 ${seconds} 秒）",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Tokens.GrayText,
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowDown
                } else {
                    Icons.Outlined.KeyboardArrowRight
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Tokens.GrayText,
            )
        }
        if (expanded && (hasThinking || hasTools)) {
            val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            // 只在「思考中・限高」时内部滚动；展开后恢复正文式自然滚动（官方设计）
            val contentModifier = Modifier
                .fillMaxWidth()
                .then(if (limited) Modifier.heightIn(max = 260.dp) else Modifier)
                .drawBehind {
                    val lineWidth = 2.dp.toPx()
                    drawRect(color = lineColor, size = Size(lineWidth, size.height))
                }
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 2.dp)
                .then(if (limited) Modifier.verticalScroll(scroll) else Modifier)
            Box(contentModifier) {
                Column {
                    var index = 0
                    while (index < message.blocks.size) {
                        val block = message.blocks[index]
                        if (block.type == BlockType.TOOL) {
                            // 连续的联网步骤合并成一个胶囊，避免一串「网页步骤」打断阅读
                            val group = mutableListOf<UiBlock>()
                            while (index < message.blocks.size &&
                                message.blocks[index].type == BlockType.TOOL
                            ) {
                                group += message.blocks[index]
                                index++
                            }
                            ToolGroupStep(group)
                            Spacer(Modifier.height(6.dp))
                            continue
                        }
                        index++
                        when {
                            block.isThinking && block.text.isNotBlank() -> {
                                // 思考链内也走 Markdown（粗体/列表/行内代码/公式），避免露出 ** 等符号
                                MarkdownText(
                                    text = block.text,
                                    fontSize = 14.sp,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    compact = true,
                                    // 公式沿用思考链的灰色与小字号，避免出现一坨黑色独立渲染
                                    mathColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    mathSize = 15.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                        }
                    }
                }
            }
        }
    }
}

/**
 * 联网步骤胶囊：把连续的搜索 / 阅读步骤收敛成一个盒子。
 * 文案区分「搜索到 N 个网页」与「已浏览 N 个页面」，解析不出信息的步骤直接不展示，
 * 不再出现「网页步骤」这种占位词。
 */
@Composable
private fun ToolGroupStep(blocks: List<UiBlock>) {
    val nav = LocalChatNavigator.current
    val infos = remember(blocks) {
        blocks.mapNotNull { ToolParser.parse("", it.payload) }
    }
    if (infos.isEmpty()) return

    val searches = infos.filter { it.isSearch }
    val fetches = infos.filter { it.isFetch }
    val sources = searches.flatMap { it.sources }
    val pages = fetches.mapNotNull { f ->
        f.url?.let { url -> ToolSource(title = f.title?.takeIf { it.isNotBlank() } ?: url, url = url) }
    }.distinctBy { it.url }
    val browsing = fetches.isNotEmpty() && pages.isEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            sources.isNotEmpty() -> ToolChip(
                icon = Icons.Outlined.Search,
                text = "搜索到 ${sources.size} 个网页",
                icons = sources,
                onClick = { nav?.openSearch(sources) },
            )
            searches.isNotEmpty() -> ToolChip(
                icon = Icons.Outlined.Search,
                text = "正在搜索…",
                onClick = null,
            )
        }
        when {
            pages.isNotEmpty() -> ToolChip(
                icon = Icons.Outlined.Link,
                text = "已浏览 ${pages.size} 个页面",
                icons = pages,
                onClick = { nav?.openBrowse(pages) },
            )
            browsing -> ToolChip(
                icon = Icons.Outlined.Link,
                text = "正在浏览…",
                onClick = null,
            )
        }
    }
}

/** 思维链里的工具胶囊：图标 + 文案 + 叠压站点圆标 + 右箭头 */
@Composable
private fun ToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    icons: List<ToolSource> = emptyList(),
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (icons.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            SiteIconStack(icons)
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 来源站点小圆标：最多叠三个，颜色由域名决定，便于一眼看出搜了哪些站 */
@Composable
private fun SiteIconStack(sources: List<ToolSource>) {
    val palette = listOf(
        Color(0xFF4F8DF7), Color(0xFF34A853), Color(0xFFF2994A),
        Color(0xFF9B51E0), Color(0xFFEB5757), Color(0xFF2D9CDB),
    )
    // 叠压用 spacedBy 的负间距：Compose 不允许负 padding（会抛 IllegalArgumentException 直接崩溃）
    Row(
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sources.take(3).forEach { source ->
            val host = runCatching { java.net.URI(source.url).host }.getOrNull()
                ?.removePrefix("www.").orEmpty()
            val letter = (source.siteName?.takeIf { it.isNotBlank() } ?: host)
                .take(1).uppercase().ifBlank { "?" }
            val color = palette[(host.hashCode().let { if (it < 0) -it else it }) % palette.size]
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(raw: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "生成失败",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = friendlyError(raw),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text("重试", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Composer(
    showDisclaimer: Boolean,
    input: String,
    streaming: Boolean,
    thinking: Boolean,
    search: Boolean,
    attachments: List<DraftAttachment>,
    balanceYuan: Double?,
    balanceWarn: Float,
    onOpenBalance: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onAddFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.Border),
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 13.dp, bottom = 9.dp)) {
                if (attachments.isNotEmpty()) {
                    AttachmentStrip(
                        items = attachments,
                        onRemove = onRemoveAttachment,
                        onRetry = onRetryAttachment,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Box {
                    if (input.isEmpty()) {
                        Text(
                            "发消息或按住说话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Tokens.Placeholder,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TogglePill(
                        label = "深度思考",
                        icon = {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF7B61FF),
                            )
                        },
                        on = thinking,
                        onClick = onToggleThinking,
                    )
                    Spacer(Modifier.width(7.dp))
                    TogglePill(
                        label = "智能搜索",
                        icon = {
                            Icon(
                                Icons.Outlined.Public,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Tokens.Blue500,
                            )
                        },
                        on = search,
                        onClick = onToggleSearch,
                    )
                    Spacer(Modifier.weight(1f))
                    if (input.isBlank() && attachments.isEmpty() && !streaming) {
                        var attachMenu by remember { mutableStateOf(false) }
                        Box {
                            CircleIconButton(onClick = { attachMenu = true }) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = "附件",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            AppDropdownMenu(
                                expanded = attachMenu,
                                onDismissRequest = { attachMenu = false },
                            ) {
                                PopupMenuItem("拍照", Icons.Outlined.PhotoCamera) {
                                    attachMenu = false
                                    onTakePhoto()
                                }
                                MenuDivider()
                                PopupMenuItem("选择文件", Icons.Outlined.AttachFile) {
                                    attachMenu = false
                                    onAddFiles()
                                }
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                        CircleIconButton(
                            onClick = {
                                Toast.makeText(context, "语音输入暂缓，后续版本提供", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = "语音",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        SendButton(
                            streaming = streaming,
                            enabled = input.isNotBlank() || attachments.isNotEmpty(),
                            onSend = onSend,
                            onStop = onStop,
                        )
                    }
                }
            }
        }
        // 左下角常显：余额（未知时显示「--」）+ 峰谷标记。
        // 之前余额为空时整块一起不渲染，看起来就像「换个主题小字就没了」——真正取决于有没有余额数据。
        Spacer(Modifier.height(6.dp))
        val low = balanceYuan != null && balanceYuan < balanceWarn
        Box(Modifier.fillMaxWidth()) {
            if (showDisclaimer) {
                Text(
                    text = "内容由 AI 生成，仅供参考",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Tokens.Placeholder.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenBalance)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = balanceYuan?.let { "余额 ¥${String.format("%.2f", it)}" } ?: "余额 --",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (low) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (low) MaterialTheme.colorScheme.error else Tokens.Placeholder,
                )
                Spacer(Modifier.width(4.dp))
                // 峰时段红色「峰」，谷时段绿色「谷」
                PeakValleyTag()
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    items: List<DraftAttachment>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 6.dp, end = 6.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Box {
                when {
                    item.isImage -> Box(
                        Modifier
                            .size(64.dp)
                            .clickable(enabled = item.status == UploadStatus.FAILED) { onRetry(item.id) },
                    ) {
                        AttachmentImage(
                            uri = item.uri.toString(),
                            modifier = Modifier.fillMaxSize(),
                            corner = 12,
                        )
                        if (item.status != UploadStatus.DONE) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (item.status == UploadStatus.FAILED) {
                                        "重试"
                                    } else {
                                        "${(item.progress * 100).toInt()}%"
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    else -> DraftFileCard(item, onRetry)
                }

                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC303030))
                        .clickable { onRemove(item.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "移除附件",
                        modifier = Modifier.size(12.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftFileCard(item: DraftAttachment, onRetry: (String) -> Unit) {
    val failed = item.status == UploadStatus.FAILED
    Surface(
        color = if (LocalDarkTheme.current) {
            Tokens.BubbleDark
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        border = if (failed) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = failed) { onRetry(item.id) },
    ) {
        Row(
            Modifier.padding(8.dp).widthIn(max = 210.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 150.dp)) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (item.status) {
                        UploadStatus.UPLOADING -> "上传中 ${(item.progress * 100).toInt()}%"
                        UploadStatus.FAILED -> item.error ?: "上传失败，点击重试"
                        UploadStatus.DONE -> listOf(item.ext, item.sizeText)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    },
                    fontSize = 11.sp,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 用户消息内的附件：**横排一行、可左右滑动**（对齐官方）。
 */
@Composable
private fun UserAttachments(
    attachments: List<UiAttachment>,
    onPreviewImage: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(attachments, key = { it.id }) { item ->
            if (item.isImage) {
                AttachmentImage(
                    uri = item.localUri,
                    modifier = Modifier
                        .size(96.dp)
                        .clickable(enabled = !item.localUri.isNullOrBlank()) {
                            item.localUri?.let(onPreviewImage)
                        },
                    corner = 12,
                )
            } else {
                FileMessageCard(item)
            }
        }
    }
}

/** 文件卡：窄条样式（图标 + 文件名 + 类型·大小），与官方一致 */
@Composable
private fun FileMessageCard(file: UiAttachment) {
    Surface(
        color = if (LocalDarkTheme.current) {
            Tokens.BubbleDark
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .width(196.dp)
                .height(56.dp)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(file.ext, file.sizeText)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewPage(uri: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val bitmap = rememberUriImage(uri, targetSize = 2048)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = "返回",
                modifier = Modifier.size(22.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(31.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TogglePill(
    label: String,
    icon: @Composable () -> Unit,
    on: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (on) Tokens.PillText else Tokens.GrayText
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (on) Tokens.PillBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SendButton(
    streaming: Boolean,
    enabled: Boolean = true,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(if (streaming) RoundedCornerShape(12.dp) else CircleShape)
            .background(
                if (enabled || streaming) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                },
            )
            .clickable(enabled = enabled || streaming) { if (streaming) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        if (streaming) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "发送",
                modifier = Modifier.size(19.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SelectTextPage(text: String, onBack: () -> Unit) {
    SecondaryPage(title = "选择文本", onBack = onBack, scrollable = false) {
        SelectionContainer(
            Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "长按文字选择，使用系统菜单复制。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditComposer(
    text: String,
    onTextChange: (String) -> Unit,
    thinking: Boolean,
    search: Boolean,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(160)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.Border),
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "修改输入",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                keyboard?.hide()
                                onClose()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "取消修改",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TogglePill(
                        label = "深度思考",
                        icon = {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF7B61FF),
                            )
                        },
                        on = thinking,
                        onClick = onToggleThinking,
                    )
                    Spacer(Modifier.width(7.dp))
                    TogglePill(
                        label = "智能搜索",
                        icon = {
                            Icon(
                                Icons.Outlined.Public,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Tokens.Blue500,
                            )
                        },
                        on = search,
                        onClick = onToggleSearch,
                    )
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(
                        onClick = {
                            Toast.makeText(context, "文件上传将在下一版支持", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "附件",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    SendButton(
                        streaming = false,
                        enabled = text.isNotBlank(),
                        onSend = onSubmit,
                        onStop = {},
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "内容由 AI 生成，仅供参考",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Tokens.Placeholder.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

@Composable
private fun ModelPage(current: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    SecondaryPage(title = "切换模型", onBack = onBack) {
        Text(
            "切换后即时生效，按实际用量计费。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ModelOption(
            title = "DeepSeek V4.1 Flash",
            subtitle = "默认；输入约 ¥1/百万（缓存命中 ¥0.02），输出 ¥4/百万",
            selected = current == AppModel.FLASH,
        ) { onSelect(AppModel.FLASH) }
        ModelOption(
            title = "DeepSeek V4 Pro",
            subtitle = "更强推理；输入约 ¥4.5/百万（命中 ¥0.15），输出 ¥13.5/百万；不支持图片",
            selected = current == AppModel.PRO,
        ) { onSelect(AppModel.PRO) }
        Text(
            "长对话与思考模式会显著放大开销；价格按公开价目表估算，实际以账单为准。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RenameSessionPage(
    session: SessionSummary,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var value by remember(session.id) { mutableStateOf(session.title) }
    SecondaryPage(title = "重命名对话", onBack = onBack) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
        PagePrimaryButton(
            text = "保存",
            enabled = value.isNotBlank(),
        ) { onSave(value.trim()) }
    }
}

@Composable
private fun DeleteSessionPage(
    session: SessionSummary,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPage(title = "删除对话", onBack = onBack) {
        Text(
            "「${session.title.ifBlank { "新对话" }}」删除后无法恢复。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageDangerButton("删除该对话", onConfirm)
    }
}

@Composable
private fun DeleteManyPage(count: Int, onConfirm: () -> Unit, onBack: () -> Unit) {
    SecondaryPage(title = "删除对话", onBack = onBack) {
        Text(
            "将删除所选的 $count 个对话，删除后无法恢复。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageDangerButton("删除 $count 个对话", onConfirm)
    }
}

@Composable
private fun PagePrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 14.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PageDangerButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 14.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White,
        )
    }
}

/**
 * 瞬时强制跳到列表真正底部——无缓降、无补偿循环。
 *
 * 用 LazyListState 的 scrollOffset：给一个远大于任何实际视口的正偏移，
 * 令最后一条被推到视口上方，Compose 会 clamp 到列表最大滚动位置（即绝对底部）。
 * 这样既没有动画中间态（此前「先到顶部」的来源），也不依赖可能未稳定的 layoutInfo
 * 做多次补偿（此前「到底但不是最底部」的来源）。取 Int.MAX_VALUE/4 避免整数溢出。
 */
private suspend fun LazyListState.scrollToBottom(index: Int) {
    if (index < 0) return
    scrollToItem(index = index, scrollOffset = Int.MAX_VALUE / 4)
}

private data class SessionGroup(val label: String, val items: List<SessionSummary>)

private fun buildGroups(sessions: List<SessionSummary>): List<SessionGroup> {
    val pinned = sessions.filter { it.pinned }
    val rest = sessions.filterNot { it.pinned }
    val result = mutableListOf<SessionGroup>()
    if (pinned.isNotEmpty()) result += SessionGroup("置顶", pinned)
    rest.groupBy { groupLabel(it.updatedAt) }.forEach { (label, list) ->
        result += SessionGroup(label, list)
    }
    return result
}

private fun modelLabel(model: String): String = when (model) {
    AppModel.PRO -> "DeepSeek V4 Pro"
    AppModel.FLASH -> "DeepSeek V4.1 Flash"
    else -> model
}

private fun friendlyError(raw: String): String = raw.substringAfter('|', raw)

private fun groupLabel(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.isAfter(today.minusDays(7)) -> "7 天内"
        date.isAfter(today.minusDays(30)) -> "30 天内"
        else -> date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月"))
    }
}
