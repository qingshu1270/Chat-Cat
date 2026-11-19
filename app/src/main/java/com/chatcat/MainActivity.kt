package com.chatcat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatcat.data.remote.ApiException
import com.chatcat.ui.balance.BalanceScreen
import com.chatcat.ui.chat.ChatScreen
import com.chatcat.ui.chat.ChatViewModel
import com.chatcat.ui.common.ErrorMessages
import com.chatcat.ui.setup.ApiSetupPage
import com.chatcat.ui.settings.SettingsScreen
import com.chatcat.ui.theme.ChatCatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private data class Incoming(val text: String?, val uris: List<Uri>)

    /** onNewIntent 与 onCreate 共用的入口，Compose 侧订阅后注入 ViewModel */
    private val incomingFlow = MutableStateFlow<Incoming?>(null)

    /** 窗口获得输入焦点计数：Android 10+ 只有焦点窗口才允许读剪贴板 */
    private val focusFlow = MutableStateFlow(0)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusFlow.value = focusFlow.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ChatCatApp).container
        incomingFlow.value = parseIncoming(intent)

        setContent {
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(application, container),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // 回到前台 / 重新拿到焦点时再探一次剪贴板：导入引导页只在这一刻才允许读
            val focusTick by focusFlow.collectAsStateWithLifecycle()
            LaunchedEffect(focusTick, state.importGuideOpen) {
                if (!state.importGuideOpen || focusTick == 0) return@LaunchedEffect
                delay(250)
                viewModel.checkClipboardForExportLink()
            }

            // 分享 / 划词带入
            val incoming by incomingFlow.collectAsStateWithLifecycle()
            LaunchedEffect(incoming) {
                incoming?.let {
                    viewModel.receiveShared(it.text, it.uris)
                    incomingFlow.value = null
                }
            }

            // 进入应用默认即对话页；无 Key 时各入口统一进入 API 配置二级页
            var showSetup by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.requestApiSetup) {
                if (state.requestApiSetup) {
                    showSetup = true
                    viewModel.consumeApiSetupRequest()
                }
            }
            LaunchedEffect(state.hasKey) {
                if (state.hasKey && showSetup) showSetup = false
            }

            // API 配置页的本地校验状态
            var verifying by remember { mutableStateOf(false) }
            var setupError by remember { mutableStateOf<String?>(null) }

            val darkTheme = when (state.settings.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            ChatCatTheme(darkTheme = darkTheme, fontScale = state.settings.fontScale) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var reopenDrawer by remember { mutableStateOf(false) }

                // 导入引导页由 ViewModel 承载：进程/页面重建后也要能重新显示出来
                LaunchedEffect(state.importGuideOpen) {
                    if (state.importGuideOpen) showSettings = true
                }

                // 首次填 Key 前必须先同意协议：进入配置页时就把协议挡在前面，
                // 同意之后才渲染配置页（也才会去读剪贴板，避免「粘贴→被拦住→再粘贴」）。
                val needsTerms = state.settings.termsVersion < com.chatcat.ui.legal.TERMS_VERSION
                // 页面优先级：协议 > 余额 > API 配置 > 设置 > 对话；均为二级页，符合「无弹窗」准则。
                when {
                    state.termsGateOpen || (needsTerms && showSetup) -> com.chatcat.ui.legal.TermsGatePage(
                        onAccept = {
                            viewModel.acceptTerms()
                            showSetup = true
                        },
                        onReject = {
                            viewModel.closeTermsGate()
                            showSetup = false
                        },
                    )

                    state.balanceOpen -> BalanceScreen(
                        loading = state.balanceLoading,
                        info = state.balanceInfo,
                        available = state.balanceAvailable,
                        error = state.balanceError,
                        warn = state.settings.balanceWarn,
                        onWarnChange = viewModel::setBalanceWarn,
                        onRefresh = viewModel::refreshBalance,
                        onBack = viewModel::closeBalance,
                    )

                    showSetup -> ApiSetupPage(
                        verifying = verifying,
                        error = setupError,
                        onSubmit = { key ->
                            if (!viewModel.guardKeyEntry()) {
                                return@ApiSetupPage
                            }
                            scope.launch {
                                verifying = true
                                setupError = null
                                val result = container.messagesApi.verifyKey(state.settings.baseUrl, key)
                                verifying = false
                                result.onSuccess {
                                    // 新增到账户列表并设为当前，不覆盖已有的其它 Key
                                    viewModel.addApiAccount("", key)
                                }.onFailure { e ->
                                    val api = e as? ApiException
                                    // 原文与 request_id 只进日志，界面用本地化文案
                                    Log.w(
                                        "ChatCat",
                                        "verifyKey failed kind=${api?.kind} status=${api?.status} " +
                                            "request_id=${api?.requestId} msg=${api?.message}",
                                    )
                                    setupError = ErrorMessages.ofVerify(e)
                                }
                            }
                        },
                        onBack = { showSetup = false },
                        onClipboardKey = viewModel::clipboardApiKey,
                    )

                    showSettings -> SettingsScreen(
                        settings = state.settings,
                        archiving = state.archiving,
                        archiveNotice = state.archiveNotice,
                        importPreview = state.importPreview,
                        usageOverviewOpen = state.usageOverviewOpen,
                        usageOverview = state.usageOverview,
                        onBack = {
                            showSettings = false
                            reopenDrawer = true
                        },
                        onTheme = viewModel::setTheme,
                        onFontScale = viewModel::setFontScale,
                        onDefaultThinking = viewModel::setDefaultThinking,
                        onDefaultSearch = viewModel::setDefaultSearch,
                        onThinkingDisplay = viewModel::setThinkingDisplay,
                        onRenameUser = viewModel::setDisplayName,
                        onClearSessions = viewModel::clearAllSessions,
                        onExport = viewModel::exportArchive,
                        onImport = viewModel::inspectArchive,
                        onConfirmImport = viewModel::confirmImport,
                        onCancelImport = viewModel::cancelImport,
                        onBaseUrl = viewModel::setBaseUrl,
                        onOpenUsage = viewModel::openUsageOverview,
                        onCloseUsage = viewModel::closeUsageOverview,
                        onOpenBalance = viewModel::openBalance,
                        importGuideOpen = state.importGuideOpen,
                        linkStage = state.linkStage,
                        detectedLink = state.detectedLink,
                        linkPrompt = state.linkPrompt,
                        downloadProgress = state.downloadProgress,
                        linkError = state.linkError,
                        clipboardHint = state.clipboardHint,
                        linkSuccess = state.linkSuccess,
                        onOpenImportGuide = viewModel::openImportGuide,
                        onCloseImportGuide = viewModel::closeImportGuide,
                        onCheckClipboard = { viewModel.checkClipboardForExportLink() },
                        onSubmitLink = viewModel::submitLinkText,
                        onReadClipboardRaw = viewModel::clipboardRawText,
                        onFinishLinkImport = viewModel::finishLinkImport,
                        onConfirmLinkImport = viewModel::confirmLinkImport,
                        onDismissLinkPrompt = viewModel::dismissLinkPrompt,
                        onRetryLinkImport = viewModel::retryLinkImport,
                        apiAccounts = state.apiAccounts,
                        onSelectApiAccount = viewModel::selectApiAccount,
                        onRenameApiAccount = viewModel::renameApiAccount,
                        onDeleteApiAccount = viewModel::deleteApiAccount,
                        onAddApiAccount = viewModel::addApiAccount,
                        onClearKeys = {
                            viewModel.clearApiKeys()
                            showSettings = false
                            showSetup = true
                            setupError = null
                        },
                    )

                    else -> ChatScreen(
                        state = state,
                        onSelectSession = viewModel::selectSession,
                        onNewChat = viewModel::newChat,
                        onDeleteSession = viewModel::deleteSession,
                        onPinSession = viewModel::pinSession,
                        onRenameSession = viewModel::renameSession,
                        onPinSessions = viewModel::pinSessions,
                        onDeleteSessions = viewModel::deleteSessions,
                        onEditMessage = viewModel::editMessage,
                        onAddAttachments = viewModel::addAttachments,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onRetryAttachment = viewModel::retryAttachment,
                        onInput = viewModel::onInputChange,
                        onSend = viewModel::send,
                        onStop = viewModel::stop,
                        onRetry = viewModel::retry,
                        onRegenerate = viewModel::regenerate,
                        onContinue = viewModel::continueGeneration,
                        onSwitchVersion = viewModel::switchVersion,
                        onFeedback = viewModel::feedback,
                        onShowUsage = viewModel::showUsage,
                        onDismissUsage = viewModel::dismissUsage,
                        onToggleThinking = viewModel::toggleThinking,
                        onToggleSearch = viewModel::toggleSearch,
                        onRequestModel = viewModel::requestModelSwitch,
                        onConfirmModel = viewModel::confirmModelSwitch,
                        onDismissModel = viewModel::dismissModelDialog,
                        onConsumeToast = viewModel::consumeToast,
                        onOpenSettings = { showSettings = true },
                        onOpenApiSetup = { showSetup = true },
                        onDismissIntegrityAlert = viewModel::dismissIntegrityAlert,
                        onOpenBalance = viewModel::openBalance,
                        openDrawerOnStart = reopenDrawer,
                        onDrawerOpened = { reopenDrawer = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncoming(intent)?.let { incomingFlow.value = it }
    }

    /** 解析 ACTION_SEND / SEND_MULTIPLE / PROCESS_TEXT，只取文本与图片/文件 */
    private fun parseIncoming(intent: Intent?): Incoming? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND &&
            action != Intent.ACTION_SEND_MULTIPLE &&
            action != Intent.ACTION_PROCESS_TEXT
        ) return null

        val text = when (action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)
        }

        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        }
        return if (text.isNullOrBlank() && uris.isEmpty()) null else Incoming(text, uris)
    }
}
