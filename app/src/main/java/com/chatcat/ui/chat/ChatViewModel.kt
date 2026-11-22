package com.chatcat.ui.chat

import android.app.Application
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chatcat.AppContainer
import com.chatcat.data.archive.ImportMode
import com.chatcat.data.archive.ImportPreview
import com.chatcat.data.archive.ImportCache
import com.chatcat.data.archive.ImportResult
import com.chatcat.data.prefs.AppSettings
import com.chatcat.data.remote.ApiException
import com.chatcat.data.remote.ArchiveDownloadException
import com.chatcat.data.remote.DownloadFailure
import com.chatcat.data.remote.ImageCompressor
import com.chatcat.data.remote.StartBlock
import com.chatcat.data.remote.StreamEvent
import com.chatcat.domain.context.ContextAssembler
import com.chatcat.domain.model.ApiKeyParser
import com.chatcat.domain.model.AttachmentPolicy
import com.chatcat.domain.model.Citation
import com.chatcat.domain.model.ContinuePolicy
import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.ExportLinkParser
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.PeakValley
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.SessionSummary
import com.chatcat.security.SensitiveAction
import com.chatcat.security.SignatureGuard
import com.chatcat.domain.model.UsagePricing
import com.chatcat.domain.model.UsageSummary
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.UiMessage
import com.chatcat.ui.common.ClipboardText
import com.chatcat.ui.common.ErrorMessages
import com.chatcat.ui.common.clipboardPreview
import com.chatcat.ui.common.readClipboardRaw
import com.chatcat.ui.common.readClipboardText
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val MAX_FILE_RETRIES = 2

/** 主动同步余额的间隔：频率不高，够用户把握花费趋势 */
private const val BALANCE_SYNC_INTERVAL_MS = 5 * 60 * 1000L

/** 请求结束触发的余额刷新，最短间隔（连续发消息不重复打接口） */
private const val BALANCE_SYNC_MIN_INTERVAL_MS = 20 * 1000L

/** 剪贴板重试节奏：回到前台后窗口焦点需要几十到几百毫秒才稳定 */
private val SCAN_RETRY_DELAYS = longArrayOf(350, 700, 1200, 2000)

enum class UploadStatus { UPLOADING, DONE, FAILED }

/** 输入区里等待发送的附件；选中即上传，发送时直接带 file_id */
data class DraftAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val isImage: Boolean,
    val status: UploadStatus = UploadStatus.UPLOADING,
    val fileId: String? = null,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val ext: String get() = AttachmentPolicy.extension(name).uppercase()
    val sizeText: String get() = AttachmentPolicy.formatSize(size)

    fun toUi(): UiAttachment = UiAttachment(
        id = id,
        name = name,
        mime = mime,
        size = size,
        isImage = isImage,
        fileId = fileId,
        localUri = uri.toString(),
    )
}

/** 用量页展示数据：本次请求用量 + 本月累计 */
data class UsageDetail(
    val thisMessage: UsageSummary,
    val thisMonth: UsageSummary,
)

/**
 * 「从 DeepSeek 全量导入」链路状态机：
 * WAITING（等待剪贴板链接）→ DOWNLOADING → IMPORTING；失败统一进 FAILED 并露出 ZIP 降级入口。
 */
enum class LinkImportStage { IDLE, WAITING, DOWNLOADING, IMPORTING, DONE, FAILED }

/** 账户管理列表项：只携带掩码，界面无法拿到明文 Key */
data class ApiAccount(
    val id: String,
    val name: String,
    val masked: String,
    val current: Boolean,
)

data class ChatUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val sessionId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val live: UiMessage? = null,
    val input: String = "",
    val streaming: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val currentModel: String = AppSettings.DEFAULT_MODEL,
    val hasKey: Boolean = false,
    val error: String? = null,
    val modelDialog: Boolean = false,
    val pendingModel: String? = null,
    /** 一次性轻提示文案（如「已经在新对话中」） */
    val toast: String? = null,
    /** 本地消息反馈：messageId -> 1 赞 / -1 踩 */
    val feedback: Map<String, Int> = emptyMap(),
    /** 处于「空白新对话」：还没落库，发第一条消息时才建会话（启动即处于该状态） */
    val draft: Boolean = true,
    /** 归档导入/导出进行中 */
    val archiving: Boolean = false,
    /** 导入进度：已处理会话数；-1 表示当前没有导入任务 */
    val importProgress: Int = -1,
    /** 本次导入预计的会话数（取自预览），用于显示分母 */
    val importTotal: Int = 0,
    val archiveNotice: String? = null,
    /** 选中备份文件后的预览，非空时进入导入确认页 */
    val importPreview: ImportPreview? = null,
    /** 输入区待发送的附件（选中即上传） */
    val attachments: List<DraftAttachment> = emptyList(),
    /** 用量页：正在查看的消息 id（null 表示未打开） */
    val usageMessageId: String? = null,
    /** 用量页内容：本次输入/输出 + 本月累计 */
    val usageDetail: UsageDetail? = null,
    /** 设置-用量统计页是否打开 */
    val usageOverviewOpen: Boolean = false,
    val usageOverview: UsageSummary? = null,
    /** 余额页状态 */
    val balanceOpen: Boolean = false,
    val balanceLoading: Boolean = false,
    val balanceInfo: com.chatcat.data.remote.BalanceInfo? = null,
    val balanceAvailable: Boolean = false,
    val balanceError: String? = null,
    /** 无 Key 时请求打开 API 配置界面（由 Activity 消费） */
    val requestApiSetup: Boolean = false,
    /** 全量导入引导页是否打开 */
    val importGuideOpen: Boolean = false,
    /** 全量导入链路当前阶段 */
    val linkStage: LinkImportStage = LinkImportStage.IDLE,
    /** 已识别的导出下载链接（用于重试与展示域名） */
    val detectedLink: String? = null,
    /** 非空时弹出「是否下载并导入」确认框 */
    val linkPrompt: String? = null,
    /** 下载进度 0f..1f */
    val downloadProgress: Float = 0f,
    /** 链路失败原因（含剪贴板不可读的降级提示） */
    val linkError: String? = null,
    /** 剪贴板读取状态摘要：读到什么 / 没读到，用于告诉用户「为什么没识别到」 */
    val clipboardHint: String? = null,
    /** 导入成功文案（stage == DONE 时展示） */
    val linkSuccess: String? = null,
    /** API 账户列表：只含掩码，明文 Key 不进 UiState */
    val apiAccounts: List<ApiAccount> = emptyList(),
    val currentApiAccountId: String = "",
    /**
     * 当前会话的「深度思考 / 智能搜索」开关。
     * 与个性化里的默认值解耦：这里只影响当前对话，个性化只影响新对话的初始值。
     */
    val thinking: Boolean = true,
    val search: Boolean = false,
    /** 首次填写 Key / 使用敏感能力前必须同意的用户协议页 */
    val termsGateOpen: Boolean = false,
    /** 非空时弹「应用签名异常」提示，内容是本次被拦下的操作名 */
    val integrityAlert: String? = null,
    /** 左下角常显余额（CNY 元）；null 表示尚未取到 */
    val balanceYuan: Double? = null,
    /** 余额最近一次同步时间（毫秒），用于避免频繁请求 */
    val balanceSyncedAt: Long = 0L,
) {
    val visibleMessages: List<UiMessage>
        get() {
            val current = live ?: return messages
            val index = messages.indexOfFirst { it.id == current.id }
            return if (index >= 0) {
                messages.toMutableList().also { it[index] = current }
            } else {
                messages + current
            }
        }
}

class ChatViewModel(
    app: Application,
    private val container: AppContainer,
) : AndroidViewModel(app) {

    private val repo = container.repository
    private val api = container.messagesApi
    private val settingsStore = container.settings

    private val _state = MutableStateFlow(
        ChatUiState(),
    )
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var sendJob: Job? = null
    private var lastPublish = 0L
    private var pendingImportUri: Uri? = null
    /** 链接下载得到的临时包：确认或取消导入后删除 */
    private var pendingLinkFile: File? = null
    /** 全量导入：下载 + 导入任务，退出引导页时取消 */
    private var linkImportJob: Job? = null
    /** 剪贴板重试任务（回到前台瞬间往往读不到，需要分几次重试） */
    private var clipboardJob: Job? = null
    /** 已经弹过确认框的链接，避免每次回到前台都打扰 */
    private var promptedLink: String? = null
    private val uploadJobs = mutableMapOf<String, Job>()
    /** 输入草稿按会话保存：切换会话/新建对话后切回不丢（"" 表示空白新对话） */
    private val drafts = mutableMapOf<String, String>()

    /** 每个会话自己的思考/搜索开关（"" 表示尚未落库的新对话），不写回个性化设置 */
    private val sessionToggles = mutableMapOf<String, Pair<Boolean, Boolean>>()
    private val uploadSlots = Semaphore(3)

    init {
        // 节假日：先用本地缓存，再在后台按 12 小时节流刷新（失败保留旧数据）
        viewModelScope.launch {
            runCatching {
                val cached = container.holidays.cached()
                if (cached.isNotEmpty()) PeakValley.setHolidays(cached)
                val latest = container.holidays.ensureFresh()
                if (latest.isNotEmpty()) PeakValley.setHolidays(latest)
            }
        }

        // 启动时读取已保存的 Key：无论有无 Key 都直接到对话页，提示条据 hasKey 显示
        refreshKeyState()
        // 低频余额同步（5 分钟一次）：启动先取一次，之后定时；失败静默
        viewModelScope.launch {
            syncBalanceQuietly(force = true)
            while (true) {
                delay(BALANCE_SYNC_INTERVAL_MS)
                syncBalanceQuietly(force = true)
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _state.update {
                    val key = it.sessionId ?: ""
                    val own = sessionToggles[key]
                    it.copy(
                        settings = s,
                        currentModel = if (it.sessionId == null) s.model else it.currentModel,
                        // 已经在本会话里手动开关过，就不再被个性化默认值覆盖
                        thinking = own?.first ?: if (it.sessionId == null) s.thinking else it.thinking,
                        search = own?.second ?: if (it.sessionId == null) s.search else it.search,
                    )
                }
            }
        }
        viewModelScope.launch {
            // 清掉重复点「新建」留下的空会话（旧版本遗留），再开始观察列表
            runCatching { repo.deleteEmptySessions("") }
            repo.sessions().collect { list ->
                // 只更新列表，不再自动打开上次的对话：每次启动都停在「新建对话」空白页
                _state.update { it.copy(sessions = list) }
            }
        }
    }

    /**
     * 敏感操作前置校验：导入数据 / 充值 / 填 Key 之前先确认安装包签名。
     * 签名被改过（二次打包）时只提示、不执行，避免把数据交给盗版包。
     */
    private inline fun officialOnly(action: SensitiveAction, block: () -> Unit) {
        if (SignatureGuard.allowSensitive(getApplication())) {
            block()
        } else {
            _state.update { it.copy(integrityAlert = action.label) }
        }
    }

    /** 协议是否已同意到当前版本 */
    fun termsAccepted(): Boolean =
        _state.value.settings.termsVersion >= com.chatcat.ui.legal.TERMS_VERSION

    fun openTermsGate() {
        _state.update { it.copy(termsGateOpen = true) }
    }

    fun closeTermsGate() {
        _state.update { it.copy(termsGateOpen = false) }
    }

    fun acceptTerms() {
        viewModelScope.launch {
            settingsStore.update { it.copy(termsVersion = com.chatcat.ui.legal.TERMS_VERSION) }
            _state.update { it.copy(termsGateOpen = false) }
        }
    }

    /**
     * 填写 API Key 前的统一闸门：先过协议，再过签名校验。
     * 两步都通过才允许继续。
     */
    fun guardKeyEntry(): Boolean {
        if (!termsAccepted()) {
            openTermsGate()
            return false
        }
        return guardSensitive(SensitiveAction.SAVE_API_KEY)
    }

    /** 供界面直接调用：返回是否放行，不放行时自动弹提示 */
    fun guardSensitive(action: SensitiveAction): Boolean {
        val ok = SignatureGuard.allowSensitive(getApplication())
        if (!ok) _state.update { it.copy(integrityAlert = action.label) }
        return ok
    }

    fun dismissIntegrityAlert() {
        _state.update { it.copy(integrityAlert = null) }
    }

    fun refreshKeyState() {
        val entries = settingsStore.apiKeyEntries()
        val currentId = settingsStore.currentApiKeyId()
        _state.update {
            it.copy(
                hasKey = settingsStore.apiKey.isNotBlank(),
                apiAccounts = entries.map { e ->
                    ApiAccount(id = e.id, name = e.name, masked = e.masked, current = e.id == currentId)
                },
                currentApiAccountId = currentId,
            )
        }
    }

    // ---------- API 账户管理 ----------

    /** 新增（或复用同 Key 的）账户并设为当前，不影删除其它账户 */
    fun addApiAccount(name: String, key: String) {
        if (!termsAccepted()) {
            openTermsGate()
            return
        }
        settingsStore.addApiKey(name, key)
        refreshKeyState()
        syncBalanceQuietly(force = true)
    }

    fun selectApiAccount(id: String) {
        settingsStore.selectApiKey(id)
        refreshKeyState()
        _state.update { it.copy(balanceYuan = null, balanceSyncedAt = 0L) }
        syncBalanceQuietly(force = true)
    }

    fun renameApiAccount(id: String, name: String) {
        settingsStore.renameApiKey(id, name)
        refreshKeyState()
    }

    fun deleteApiAccount(id: String) {
        settingsStore.deleteApiKey(id)
        refreshKeyState()
        if (settingsStore.apiKey.isBlank()) {
            _state.update { it.copy(balanceYuan = null, balanceSyncedAt = 0L) }
        } else {
            syncBalanceQuietly(force = true)
        }
    }

    /** 清除全部已保存的 Key（设置页「清除 API Key」） */
    fun clearApiKeys() {
        settingsStore.apiKey = ""
        refreshKeyState()
        _state.update {
            it.copy(balanceYuan = null, balanceInfo = null, balanceSyncedAt = 0L)
        }
    }

    fun selectSession(id: String) {
        if (_state.value.sessionId == id && sessionJob != null) return
        sessionJob?.cancel()
        // 先记住当前会话的输入，再切到目标会话的草稿
        drafts[_state.value.sessionId ?: ""] = _state.value.input
        clearDraftAttachments()
        val own = sessionToggles[id]
        _state.update {
            it.copy(
                sessionId = id,
                draft = false,
                thinking = own?.first ?: it.settings.thinking,
                search = own?.second ?: it.settings.search,
                input = drafts[id].orEmpty(),
                messages = emptyList(),
                live = null,
                streaming = false,
                error = null,
            )
        }
        sessionJob = viewModelScope.launch {
            repo.conversation(id).collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch {
            repo.session(id)?.let { s ->
                _state.update { it.copy(currentModel = s.model) }
            }
            settingsStore.update { it.copy(lastSessionId = id) }
        }
    }

    /**
     * 官方行为：已经处在一个空的新对话里，再点「新建」只提示「已经在新对话中」，
     * 不再创建新的空会话（避免历史列表堆一串空对话）。
     */
    fun newChat() {
        val s = _state.value
        if (s.streaming) return
        // 已经在一个空白新对话里：官方只提示「已经在新对话中」，不再新建
        if (s.messages.isEmpty()) {
            _state.update { it.copy(toast = "已经在新对话中") }
            return
        }
        sessionJob?.cancel()
        sessionJob = null
        drafts[_state.value.sessionId ?: ""] = _state.value.input
        clearDraftAttachments()
        sessionToggles.remove("")
        _state.update {
            it.copy(
                sessionId = null,
                draft = true,
                // 新对话从「个性化」里的默认开关起步
                thinking = it.settings.thinking,
                search = it.settings.search,
                input = drafts[""].orEmpty(),
                messages = emptyList(),
                live = null,
                streaming = false,
                error = null,
            )
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            if (_state.value.sessionId == id) {
                sessionJob?.cancel()
                sessionJob = null
                _state.update { it.copy(sessionId = null, messages = emptyList(), live = null) }
            }
            repo.deleteSession(id)
        }
    }

    fun pinSession(id: String, pinned: Boolean) {
        viewModelScope.launch { repo.pinSession(id, pinned) }
    }

    fun pinSessions(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch { ids.forEach { repo.pinSession(it, pinned) } }
    }

    fun deleteSessions(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val current = _state.value.sessionId
            if (current != null && current in ids) {
                sessionJob?.cancel()
                sessionJob = null
                _state.update {
                    it.copy(sessionId = null, messages = emptyList(), live = null, draft = false)
                }
            }
            ids.forEach { repo.deleteSession(it) }
        }
    }

    /**
     * 修改提问：旧提问及其后续整体转入历史分支（保留数据），再用新内容重新生成。
     */
    fun editMessage(messageId: String, newText: String) {
        if (_state.value.streaming) {
            _state.update { it.copy(toast = "正在生成，请稍后再试") }
            return
        }
        val value = newText.trim()
        if (value.isEmpty()) return
        val messages = _state.value.messages
        val index = messages.indexOfFirst { it.id == messageId && it.role == Role.USER }
        if (index < 0) return
        val target = messages[index]
        val history = messages.take(index)
        _state.update { it.copy(messages = history) }
        viewModelScope.launch {
            _state.value.sessionId?.let { repo.deactivateFrom(it, target.seq) }
            sendText(
                value,
                historyOverride = history,
                attachments = target.attachments,
                newUserParentId = target.id,
            )
        }
    }

    fun exportArchive(uri: Uri) {
        if (_state.value.archiving) return
        viewModelScope.launch {
            _state.update { it.copy(archiving = true, archiveNotice = null) }
            val result = runCatching {
                container.archive.export(getApplication<Application>().contentResolver, uri)
            }
            result.onFailure { Log.w("ChatCat", "export failed", it) }
            _state.update {
                it.copy(
                    archiving = false,
                    archiveNotice = result.fold(
                        onSuccess = { count -> "已导出 $count 个对话" },
                        onFailure = { "导出失败，请重试" },
                    ),
                )
            }
        }
    }

    /** 读取备份元信息（会话数、user.json 中的显示名），进入导入确认页 */
    fun inspectArchive(uri: Uri) {
        if (!SignatureGuard.allowSensitive(getApplication())) {
            _state.update { it.copy(integrityAlert = SensitiveAction.IMPORT_DATA.label) }
            return
        }
        if (_state.value.archiving) return
        viewModelScope.launch {
            _state.update { it.copy(archiving = true, archiveNotice = null) }
            val result = runCatching {
                container.archive.inspect(getApplication<Application>().contentResolver, uri)
            }
            result.onFailure { Log.w("ChatCat", "inspect archive failed", it) }
            if (result.isSuccess) pendingImportUri = uri
            _state.update {
                it.copy(
                    archiving = false,
                    importPreview = result.getOrNull(),
                    // 选择本地文件即视为走降级路径，收起引导页
                    importGuideOpen = if (result.isSuccess) false else it.importGuideOpen,
                    linkStage = if (result.isSuccess) LinkImportStage.IDLE else it.linkStage,
                    linkPrompt = null,
                    archiveNotice = result.exceptionOrNull()?.let { archiveFailureText(it, preview = true) },
                )
            }
        }
    }

    /**
     * 确认导入：按用户选择合并增量或覆盖替换；按用户选择决定是否保留 user.json 中的显示名。
     * 链接下载得到的临时包同样走这里，导入后立即删除。
     */
    fun confirmImport(keepUserName: Boolean, mode: ImportMode = ImportMode.MERGE) {
        if (!SignatureGuard.allowSensitive(getApplication())) {
            _state.update { it.copy(integrityAlert = SensitiveAction.IMPORT_DATA.label) }
            return
        }
        val uri = pendingImportUri ?: return
        if (_state.value.archiving) return
        viewModelScope.launch {
            val preview = _state.value.importPreview
            _state.update {
                it.copy(
                    archiving = true,
                    archiveNotice = null,
                    importProgress = 0,
                    importTotal = preview?.sessionCount ?: 0,
                )
            }
            val result = runCatching {
                container.archive.import(
                    resolver = getApplication<Application>().contentResolver,
                    uri = uri,
                    mode = mode,
                    onProgress = { done ->
                        _state.update { it.copy(importProgress = done) }
                    },
                )
            }
            result.onFailure { Log.w("ChatCat", "import archive failed", it) }

            if (result.isSuccess) {
                if (keepUserName) {
                    val name = preview?.userName?.trim()
                    if (!name.isNullOrEmpty()) {
                        settingsStore.update { it.copy(displayName = name) }
                    }
                }
                sessionJob?.cancel()
                sessionJob = null
                pendingImportUri = null
                pendingLinkFile?.delete()
                pendingLinkFile = null
                val outcome = result.getOrThrow()
                val text = importSummary(outcome, mode)
                _state.update {
                    it.copy(
                        archiving = false,
                        importProgress = -1,
                        importTotal = 0,
                        importPreview = null,
                        sessionId = null,
                        messages = emptyList(),
                        live = null,
                        draft = true,
                        archiveNotice = text,
                        // 链接链路：停留在引导页展示结果，用户点「完成」再收起
                        linkStage = if (it.importGuideOpen) LinkImportStage.DONE else it.linkStage,
                        linkSuccess = if (it.importGuideOpen) text else it.linkSuccess,
                        linkPrompt = null,
                        downloadProgress = 0f,
                    )
                }
            } else {
                pendingLinkFile?.delete()
                pendingLinkFile = null
                _state.update {
                    it.copy(
                        archiving = false,
                        importProgress = -1,
                        importTotal = 0,
                        importPreview = null,
                        archiveNotice = archiveFailureText(result.exceptionOrNull(), preview = false),
                        linkStage = if (it.importGuideOpen) LinkImportStage.FAILED else it.linkStage,
                        linkError = if (it.importGuideOpen) {
                            archiveFailureText(result.exceptionOrNull(), preview = false)
                        } else {
                            it.linkError
                        },
                    )
                }
            }
        }
    }

    /**
     * 导入/预览失败文案。
     *
     * `OutOfMemoryError` 说明包体超出了当前设备的可用堆（旧实现把整包读进内存时会这样，
     * 严重时进程直接被系统杀掉，表现为闪退），这里给一条能指导操作的提示，而不是「不是 ZIP」。
     */
    private fun archiveFailureText(error: Throwable?, preview: Boolean): String = when {
        error is OutOfMemoryError ->
            "备份过大，设备内存不足：请分批导入（先导入一部分再导入其余），或换用内存更大的设备"
        error is SQLiteFullException ||
            error?.message?.contains("disk is full", ignoreCase = true) == true ->
            "设备存储空间不足：请清理空间后重试；已导入的对话会保留，重新导入即可补全"
        preview -> "无法读取备份：请确认是官方导出的 ZIP"
        else -> "导入失败，请确认是官方导出的 ZIP"
    }

    /** 导入结果文案：区分合并与覆盖，并说明跳过了多少已存在的对话 */
    private fun importSummary(result: ImportResult, mode: ImportMode): String = when {
        mode == ImportMode.REPLACE -> "已覆盖导入 ${result.sessions} 个对话、${result.messages} 条消息"
        result.sessions == 0 -> "没有新增对话（${result.skipped} 个已存在）"
        result.skipped > 0 -> "已合并 ${result.sessions} 个新对话、${result.messages} 条消息，" +
            "跳过 ${result.skipped} 个已存在"
        else -> "已合并 ${result.sessions} 个对话、${result.messages} 条消息"
    }

    fun cancelImport() {
        pendingImportUri = null
        pendingLinkFile?.delete()
        pendingLinkFile = null
        _state.update {
            it.copy(
                importPreview = null,
                linkStage = if (it.importGuideOpen) LinkImportStage.WAITING else it.linkStage,
            )
        }
    }

    // ---------- 从 DeepSeek 全量导入（默认模式：剪贴板链接） ----------

    /** 进入引导页：重置链路状态，并立即尝试读一次剪贴板 */
    fun openImportGuide() {
        if (!SignatureGuard.allowSensitive(getApplication())) {
            _state.update { it.copy(integrityAlert = SensitiveAction.IMPORT_DATA.label) }
            return
        }
        promptedLink = null
        pendingLinkFile?.delete()
        pendingLinkFile = null
        pendingImportUri = null
        _state.update {
            it.copy(
                importGuideOpen = true,
                linkStage = LinkImportStage.WAITING,
                detectedLink = null,
                linkPrompt = null,
                downloadProgress = 0f,
                linkError = null,
                clipboardHint = null,
                linkSuccess = null,
                importPreview = null,
            )
        }
        checkClipboardForExportLink()
    }

    fun closeImportGuide() {
        clipboardJob?.cancel()
        clipboardJob = null
        linkImportJob?.cancel()
        linkImportJob = null
        // 退出导入页就把下载的导出包删掉：此前只删「取消/成功」两条路径，
        // 直接返回或从后台划掉应用时，上百 MB 的包会一直留在私有目录里
        pendingLinkFile?.delete()
        pendingLinkFile = null
        pendingImportUri = null
        _state.update {
            it.copy(
                importGuideOpen = false,
                linkPrompt = null,
                linkStage = LinkImportStage.IDLE,
                clipboardHint = null,
            )
        }
    }

    /** 成功后由「完成」按钮收起引导页 */
    fun finishLinkImport() {
        closeImportGuide()
    }

    /**
     * 扫描剪贴板中的导出链接。由引导页在「进入页面」「回到前台」「窗口获得焦点」时调用，
     * 内部带重试：Android 10+ 要求应用位于前台且拥有输入焦点，回到前台的那一瞬间
     * `primaryClip` 往往仍返回 null，单次读取必然失败。
     *
     * @param force 用户主动点「立即检测」：忽略「同一链接只弹一次」，并把失败原因说清楚
     */
    fun checkClipboardForExportLink(force: Boolean = false) {
        if (!canScanClipboard()) return
        val found = scanClipboard(force = force, silent = !force)
        if (!found && !force) scheduleClipboardRetry()
    }

    private fun canScanClipboard(): Boolean {
        val s = _state.value
        if (!s.importGuideOpen) return false
        // 已经进入「导入确认」页时不再抢焦点弹窗
        if (s.importPreview != null) return false
        return s.linkStage != LinkImportStage.DOWNLOADING &&
            s.linkStage != LinkImportStage.IMPORTING &&
            s.linkStage != LinkImportStage.DONE
    }

    /** 读一次剪贴板；返回是否已识别到链接（识别到会置起确认弹窗） */
    private fun scanClipboard(force: Boolean, silent: Boolean): Boolean {
        when (val clip = readClipboardText(getApplication())) {
            ClipboardText.Blocked -> {
                if (!silent) {
                    _state.update {
                        it.copy(
                            clipboardHint = "系统未允许读取剪贴板",
                            linkError = "系统未允许读取剪贴板。请长按下方输入框手动粘贴链接，或改用「上传 ZIP 文件」。",
                        )
                    }
                }
            }

            ClipboardText.Empty -> {
                if (!silent) {
                    _state.update {
                        it.copy(
                            clipboardHint = "暂未读到剪贴板内容（Android 10+ 仅在应用位于前台时可读）",
                            linkError = "剪贴板为空或系统暂未放行。可点「重新检测」，或直接手动粘贴链接。",
                        )
                    }
                }
            }

            is ClipboardText.Text -> {
                val preview = clipboardPreview(clip.value)
                val link = ExportLinkParser.find(clip.value)
                if (link == null) {
                    _state.update {
                        it.copy(
                            clipboardHint = "剪贴板读到：$preview",
                            linkError = if (silent) it.linkError
                            else "剪贴板里没有识别到导出链接。可手动粘贴下载地址，或上传 ZIP 文件。",
                        )
                    }
                    return false
                }
                if (!force && link == promptedLink) return true
                promptedLink = link
                Log.i("ChatCat", "export link detected host=${link.substringAfter("://").substringBefore('/')}")
                _state.update {
                    it.copy(
                        detectedLink = link,
                        linkPrompt = link,
                        linkError = null,
                        clipboardHint = "剪贴板读到：$preview",
                        linkStage = LinkImportStage.WAITING,
                    )
                }
                return true
            }
        }
        return false
    }

    /** 回到前台后分几次重试，直到读到链接或用户离开页面 */
    private fun scheduleClipboardRetry() {
        if (clipboardJob?.isActive == true) return
        clipboardJob = viewModelScope.launch {
            for (delayMs in SCAN_RETRY_DELAYS) {
                delay(delayMs)
                if (!canScanClipboard()) return@launch
                if (scanClipboard(force = false, silent = true)) return@launch
            }
            if (!canScanClipboard()) return@launch
            _state.update {
                if (it.linkError != null) it
                else it.copy(clipboardHint = "还没有读到导出链接。复制链接后回到本页会自动识别，也可以手动粘贴。")
            }
        }
    }

    /** 手动粘贴链接：绕过剪贴板限制，直接进入确认流程 */
    fun submitLinkText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(linkError = "请先粘贴导出下载链接") }
            return
        }
        // 用户可能只复制了 "cdn.deepseek.com/..." 这样的裸地址
        val withScheme = if (!trimmed.contains(' ') && !trimmed.contains("://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val link = ExportLinkParser.findLoose(trimmed) ?: ExportLinkParser.findLoose(withScheme)
        if (link == null) {
            _state.update { it.copy(linkError = "没有识别到链接，请粘贴 https:// 开头的下载地址") }
            return
        }
        promptLink(link)
    }

    /** 把剪贴板原文填进手动输入框（读取失败返回 null） */
    fun clipboardRawText(): String? = readClipboardRaw(getApplication())

    private fun promptLink(link: String) {
        promptedLink = link
        _state.update {
            it.copy(
                detectedLink = link,
                linkPrompt = link,
                linkError = null,
                linkStage = LinkImportStage.WAITING,
            )
        }
    }

    fun dismissLinkPrompt() {
        _state.update { it.copy(linkPrompt = null) }
    }

    /** 用户在确认弹窗中选择「下载并导入」；卡片上的「下载并导入该链接」也走这里 */
    fun confirmLinkImport() {
        val link = _state.value.linkPrompt ?: _state.value.detectedLink ?: return
        _state.update { it.copy(linkPrompt = null) }
        startLinkImport(link)
    }

    fun retryLinkImport() {
        val link = _state.value.detectedLink ?: return
        startLinkImport(link)
    }

    private fun startLinkImport(url: String) {
        if (linkImportJob?.isActive == true) return
        linkImportJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val cache = File(app.cacheDir, "deepseek_import_${System.currentTimeMillis()}.zip")
            // 先清掉更早的失败残留，避免多份大包叠在缓存里
            runCatching { ImportCache.sweepAll(app.cacheDir, keep = pendingLinkFile) }
            _state.update {
                it.copy(linkStage = LinkImportStage.DOWNLOADING, downloadProgress = 0f, linkError = null)
            }
            var keepCache = false
            try {
                container.archiveDownloader.download(url, cache) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
                // 下载完先解析预览：让用户看清条数并选择「合并增量 / 覆盖」，不再直接把本机历史替换掉
                val preview = container.archive.inspect(cache)
                pendingImportUri = Uri.fromFile(cache)
                pendingLinkFile = cache
                keepCache = true
                _state.update {
                    it.copy(
                        linkStage = LinkImportStage.WAITING,
                        importPreview = preview,
                        downloadProgress = 0f,
                        linkPrompt = null,
                        linkError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchiveDownloadException) {
                Log.w("ChatCat", "download export failed reason=${e.reason} status=${e.cause?.message}")
                _state.update {
                    it.copy(linkStage = LinkImportStage.FAILED, linkError = downloadErrorText(e.reason))
                }
            } catch (e: Exception) {
                Log.w("ChatCat", "link import failed", e)
                _state.update {
                    it.copy(
                        linkStage = LinkImportStage.FAILED,
                        linkError = "导出包解析失败：文件可能不完整，请重试或改为上传 ZIP 文件",
                    )
                }
            } finally {
                // 包已交给导入确认页时保留，成功/取消时由 confirmImport / cancelImport 删除
                if (!keepCache) cache.delete()
                linkImportJob = null
            }
        }
    }

    private fun downloadErrorText(reason: DownloadFailure): String = when (reason) {
        DownloadFailure.EXPIRED -> "下载链接已失效，请回到 DeepSeek 重新获取后再试"
        DownloadFailure.TOO_LARGE -> "导出包超过 512 MB，请改为上传 ZIP 文件"
        DownloadFailure.NOT_ARCHIVE -> "该链接不是导出压缩包，请确认复制的是「下载链接」"
        DownloadFailure.NETWORK -> "网络异常，下载中断，请重试或改为上传 ZIP 文件"
        DownloadFailure.UNAVAILABLE -> "下载服务暂时不可用，请稍后重试或改为上传 ZIP 文件"
    }

    /**
     * 手动清理导入缓存（数据管理页），返回释放的字节数并给出提示。
     * 正常情况下启动时已经自动清过一次，这里是给用户的兜底入口。
     */
    fun clearImportCache() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val freed = runCatching { ImportCache.sweepAll(app.cacheDir) }.getOrDefault(0L)
            Log.w("ChatCat", "clear import cache freed=$freed")
            _state.update {
                it.copy(
                    archiveNotice = if (freed > 0) {
                        "已清理导入缓存 ${ImportCache.formatSize(freed)}"
                    } else {
                        "没有需要清理的导入缓存"
                    },
                )
            }
        }
    }

    fun clearArchiveNotice() {
        _state.update { it.copy(archiveNotice = null) }
    }

    fun setBaseUrl(value: String) {
        viewModelScope.launch { settingsStore.update { it.copy(baseUrl = value) } }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settingsStore.update { it.copy(theme = value) } }
    }

    /** 思考链展示方式（个性化）：limited / collapsed / full */
    fun setThinkingDisplay(value: String) {
        val normalized = when (value) {
            AppSettings.THINKING_COLLAPSED -> AppSettings.THINKING_COLLAPSED
            AppSettings.THINKING_FULL -> AppSettings.THINKING_FULL
            else -> AppSettings.THINKING_LIMITED
        }
        viewModelScope.launch {
            settingsStore.update { it.copy(thinkingDisplay = normalized) }
        }
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim().take(24)
        viewModelScope.launch {
            settingsStore.update { it.copy(displayName = trimmed) }
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch {
            settingsStore.update {
                it.copy(fontScale = value.coerceIn(AppSettings.FONT_SCALE_MIN, AppSettings.FONT_SCALE_MAX))
            }
        }
    }

    /** 余额预警线：低于该值时界面用提示色（保留 1 位小数，避免滑块刷出长尾） */
    fun setBalanceWarn(value: Float) {
        val clamped = value.coerceIn(AppSettings.BALANCE_WARN_MIN, AppSettings.BALANCE_WARN_MAX)
        viewModelScope.launch {
            settingsStore.update { it.copy(balanceWarn = clamped) }
        }
    }

    // ---------- 用量 ----------

    /** 点击某条回答的「用量」入口：加载本次 + 本月累计 */
    fun showUsage(messageId: String) {
        viewModelScope.launch {
            val thisMessage = repo.usageOfMessage(messageId)
            val monthStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val month = repo.usageSince(monthStart)
            _state.update {
                it.copy(
                    usageMessageId = messageId,
                    usageDetail = UsageDetail(thisMessage, month),
                )
            }
        }
    }

    fun dismissUsage() {
        _state.update { it.copy(usageMessageId = null, usageDetail = null) }
    }

    /** 设置-用量统计：打开本月累计 */
    fun openUsageOverview() {
        viewModelScope.launch {
            val monthStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val month = repo.usageSince(monthStart)
            _state.update { it.copy(usageOverviewOpen = true, usageOverview = month) }
        }
    }

    fun closeUsageOverview() {
        _state.update { it.copy(usageOverviewOpen = false) }
    }

    // ---------- 余额 ----------

    /** 打开余额页并查询；无 Key 时改为请求进入 API 配置 */
    /** 余额预警线（元），供界面配色判断 */
    fun balanceWarnLine(): Double = _state.value.settings.balanceWarn.toDouble()

    /**
     * 后台余额同步：不显示 loading、不打扰用户。
     * 双刷新策略之一（另一处是每次请求结束后）；[force] 用于切换账户等必须立即校正的场景。
     */
    private fun syncBalanceQuietly(force: Boolean = false) {
        val key = settingsStore.apiKey
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        if (!force && now - _state.value.balanceSyncedAt < BALANCE_SYNC_MIN_INTERVAL_MS) return
        viewModelScope.launch {
            val result = runCatching {
                container.balanceApi.fetch(_state.value.settings.baseUrl, key)
            }
            result.onSuccess { resp ->
                _state.update {
                    it.copy(
                        balanceYuan = resp.primary()?.totalBalance?.toDoubleOrNull(),
                        balanceAvailable = resp.isAvailable,
                        balanceInfo = resp.primary() ?: it.balanceInfo,
                        balanceSyncedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun openBalance() {
        if (!SignatureGuard.allowSensitive(getApplication())) {
            _state.update { it.copy(integrityAlert = SensitiveAction.RECHARGE.label) }
            return
        }
        if (settingsStore.apiKey.isBlank()) {
            _state.update { it.copy(requestApiSetup = true) }
            return
        }
        _state.update { it.copy(balanceOpen = true, balanceError = null) }
        refreshBalance()
    }

    fun closeBalance() {
        _state.update { it.copy(balanceOpen = false) }
    }

    fun refreshBalance() {
        if (settingsStore.apiKey.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(balanceLoading = true, balanceError = null) }
            val result = runCatching {
                container.balanceApi.fetch(_state.value.settings.baseUrl, settingsStore.apiKey)
            }
            result.onSuccess { resp ->
                _state.update {
                    it.copy(
                        balanceLoading = false,
                        balanceInfo = resp.primary(),
                        balanceAvailable = resp.isAvailable,
                        balanceYuan = resp.primary()?.totalBalance?.toDoubleOrNull(),
                        balanceSyncedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { e ->
                val kind = (e as? ApiException)?.kind ?: ApiException.Kind.UNKNOWN
                _state.update {
                    it.copy(balanceLoading = false, balanceError = ErrorMessages.of(kind))
                }
            }
        }
    }

    /** 无 Key 触发的 API 配置请求已被 Activity 消费 */
    fun consumeApiSetupRequest() {
        _state.update { it.copy(requestApiSetup = false) }
    }

    /**
     * API 配置页回到前台时探测剪贴板中的 Key（用户在开放平台创建后「复制」）。
     * 返回 null 表示没有可填的新 Key；UI 据此弹窗询问是否填入。
     */
    fun clipboardApiKey(): String? {
        val clip = readClipboardText(getApplication())
        if (clip !is ClipboardText.Text) return null
        val key = ApiKeyParser.find(clip.value) ?: return null
        return key.takeIf { it != settingsStore.apiKey }
    }

    fun setDefaultThinking(value: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(thinking = value) } }
    }

    fun setDefaultSearch(value: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(search = value) } }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            sessionJob?.cancel()
            sessionJob = null
            clearDraftAttachments()
            repo.deleteAllSessions()
            _state.update {
                it.copy(
                    sessionId = null,
                    messages = emptyList(),
                    live = null,
                    draft = true,
                    streaming = false,
                )
            }
        }
    }

    fun renameSession(id: String, title: String) {
        val value = title.trim()
        if (value.isEmpty()) return
        viewModelScope.launch { repo.renameSession(id, value) }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /**
     * 接收系统分享 / 划词提问（J1、J2）：文本填入输入框，图片/文件进附件区。
     * 若当前正处流式输出，先停止，避免输入与回答串在一起。
     */
    fun receiveShared(text: String?, uris: List<Uri>) {
        if (_state.value.streaming) stop()
        val cleaned = text?.trim().orEmpty()
        if (cleaned.isNotEmpty()) {
            val current = _state.value.input
            val merged = if (current.isBlank()) cleaned else "$current\n$cleaned"
            _state.update { it.copy(input = merged) }
        }
        if (uris.isNotEmpty()) addAttachments(uris)
    }

    // ---------- 附件 ----------

    private data class FileMeta(val uri: Uri, val name: String, val mime: String, val size: Long)

    /** 选中即上传（官方行为）；整批校验通过才加入，失败给出中文原因 */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val metas = uris.mapNotNull { uri ->
                // 关闭应用后缩略图仍要能读，尽力申请持久化读权限（部分来源不支持）
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                runCatching { readMeta(resolver, uri) }.getOrNull()
            }
            if (metas.isEmpty()) {
                _state.update { it.copy(toast = "无法读取所选文件") }
                return@launch
            }
            val reason = AttachmentPolicy.validate(
                files = metas.map { it.name to it.size },
                currentCount = _state.value.attachments.size,
            )
            if (reason != null) {
                _state.update { it.copy(toast = reason) }
                return@launch
            }
            val drafts = metas.map { meta ->
                DraftAttachment(
                    id = UUID.randomUUID().toString(),
                    uri = meta.uri,
                    name = meta.name,
                    mime = meta.mime,
                    size = meta.size,
                    isImage = AttachmentPolicy.isImage(meta.mime, meta.name),
                )
            }
            _state.update { it.copy(attachments = it.attachments + drafts) }
            drafts.forEach { uploadAttachment(it) }
        }
    }

    fun removeAttachment(id: String) {
        uploadJobs.remove(id)?.cancel()
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    fun retryAttachment(id: String) {
        val draft = _state.value.attachments.firstOrNull { it.id == id } ?: return
        if (draft.status == UploadStatus.UPLOADING) return
        updateAttachment(id) { it.copy(status = UploadStatus.UPLOADING, progress = 0f, error = null) }
        uploadAttachment(draft)
    }

    /** 统一上传入口：图片先压缩，临时文件用完即删 */
    private suspend fun uploadOne(
        uri: Uri,
        name: String,
        mime: String,
        isImage: Boolean,
        size: Long,
        onProgress: (Float) -> Unit,
    ): String {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val compressed = if (isImage) {
            ImageCompressor.compress(resolver, uri, app.cacheDir, size)
        } else {
            null
        }
        val uploadUri = compressed?.let { Uri.fromFile(it) } ?: uri
        val uploadName = if (compressed != null) name.substringBeforeLast('.', name) + ".jpg" else name
        try {
            return container.filesApi.upload(
                baseUrl = _state.value.settings.baseUrl,
                apiKey = settingsStore.apiKey,
                resolver = resolver,
                uri = uploadUri,
                name = uploadName,
                mime = if (compressed != null) "image/jpeg" else mime,
                purpose = AttachmentPolicy.purpose(uploadName, isImage),
                onProgress = onProgress,
            )
        } finally {
            compressed?.delete()
        }
    }

    private fun uploadAttachment(draft: DraftAttachment) {
        uploadJobs.remove(draft.id)?.cancel()
        uploadJobs[draft.id] = viewModelScope.launch {
            uploadSlots.withPermit {
                try {
                    val fileId = uploadOne(
                        uri = draft.uri,
                        name = draft.name,
                        mime = draft.mime,
                        isImage = draft.isImage,
                        size = draft.size,
                        onProgress = { progress ->
                            updateAttachment(draft.id) { it.copy(progress = progress) }
                        },
                    )
                    updateAttachment(draft.id) {
                        it.copy(status = UploadStatus.DONE, fileId = fileId, progress = 1f, error = null)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    Log.w("ChatCat", "upload failed kind=${e.kind} request_id=${e.requestId}")
                    updateAttachment(draft.id) {
                        it.copy(status = UploadStatus.FAILED, error = uploadErrorText(e.kind))
                    }
                } catch (e: Exception) {
                    updateAttachment(draft.id) {
                        it.copy(status = UploadStatus.FAILED, error = "上传失败")
                    }
                } finally {
                    uploadJobs.remove(draft.id)
                }
            }
        }
    }

    private fun uploadErrorText(kind: ApiException.Kind): String = when (kind) {
        ApiException.Kind.UPLOAD_TOO_LARGE -> "文件过大"
        ApiException.Kind.PARSE_FAILED -> "解析失败"
        ApiException.Kind.RATE_LIMIT -> "上传过于频繁，请稍后重试"
        ApiException.Kind.SERVER_BUSY -> "服务器繁忙"
        ApiException.Kind.AUTH -> "API Key 无效"
        ApiException.Kind.NETWORK -> "网络异常"
        else -> "上传失败"
    }

    private fun updateAttachment(id: String, transform: (DraftAttachment) -> DraftAttachment) {
        _state.update { s ->
            s.copy(attachments = s.attachments.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun readMeta(resolver: android.content.ContentResolver, uri: Uri): FileMeta? {
        var name: String? = null
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val mime = resolver.getType(uri).orEmpty()
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        return FileMeta(uri, name ?: fallback, mime, size.coerceAtLeast(0))
    }

    private fun clearDraftAttachments() {
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        _state.update { it.copy(attachments = emptyList()) }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun toggleThinking() {
        val next = !_state.value.thinking
        val key = _state.value.sessionId ?: ""
        sessionToggles[key] = next to _state.value.search
        _state.update { it.copy(thinking = next) }
    }

    fun toggleSearch() {
        val next = !_state.value.search
        val key = _state.value.sessionId ?: ""
        sessionToggles[key] = _state.value.thinking to next
        _state.update { it.copy(search = next) }
    }

    fun requestModelSwitch(model: String) {
        if (model == _state.value.currentModel) return
        _state.update { it.copy(modelDialog = true, pendingModel = model) }
    }

    fun dismissModelDialog() = _state.update { it.copy(modelDialog = false, pendingModel = null) }

    fun confirmModelSwitch() {
        val model = _state.value.pendingModel ?: return
        viewModelScope.launch {
            _state.value.sessionId?.let { repo.updateSessionModel(it, model) }
            settingsStore.update { it.copy(model = model) }
            _state.update { it.copy(currentModel = model, modelDialog = false, pendingModel = null) }
        }
    }

    fun send() {
        if (_state.value.streaming) return
        // 未接入 API：不发起注定失败的请求，改为引导用户配置 Key
        if (settingsStore.apiKey.isBlank()) {
            _state.update { it.copy(requestApiSetup = true) }
            return
        }
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() && s.attachments.isEmpty()) return
        if (s.attachments.any { it.status == UploadStatus.UPLOADING }) {
            _state.update { it.copy(toast = "附件上传中，请稍候") }
            return
        }
        if (s.attachments.any { it.status == UploadStatus.FAILED }) {
            _state.update { it.copy(toast = "有附件上传失败，请重试或删除") }
            return
        }
        if (s.attachments.count { !it.isImage } > AttachmentPolicy.MAX_DOCS_PER_REQUEST) {
            _state.update { it.copy(toast = "单次最多发送 ${AttachmentPolicy.MAX_DOCS_PER_REQUEST} 个文档") }
            return
        }
        if (!AttachmentPolicy.supportsImages(s.currentModel) && s.attachments.any { it.isImage }) {
            _state.update { it.copy(toast = "V4 Pro 不支持图片，请切换到 V4.1 Flash") }
            return
        }
        sendText(
            text = text,
            attachments = s.attachments.map { it.toUi() },
            clearDrafts = true,
        )
    }

    /** 失败重试：删掉失败回答与其提问，原样重发。 */
    fun retry() {
        if (_state.value.streaming) return
        val messages = _state.value.messages
        val failedIndex = messages.indexOfLast {
            it.role == Role.ASSISTANT && it.status == MessageStatus.FAILED
        }
        if (failedIndex < 0) return
        val userIndex = (failedIndex - 1).takeIf { it >= 0 } ?: return
        val user = messages[userIndex]
        if (user.role != Role.USER) return
        val history = messages.take(userIndex)
        viewModelScope.launch {
            repo.deleteMessage(messages[failedIndex].id)
            repo.deleteMessage(user.id)
            _state.update {
                it.copy(
                    messages = it.messages.filterNot { m ->
                        m.id == messages[failedIndex].id || m.id == user.id
                    },
                    error = null,
                )
            }
            sendText(user.text, historyOverride = history, attachments = user.attachments)
        }
    }

    /**
     * 重新生成 = 新增一个回答版本，旧回答保留，可用 `‹ 2/3 ›` 切回（与官方一致）。
     */
    fun regenerate() {
        if (_state.value.streaming) return
        val messages = _state.value.messages
        val index = messages.indexOfLast { it.role == Role.ASSISTANT && !it.isStreaming }
        if (index < 0) return
        val prev = messages.getOrNull(index - 1)?.takeIf { it.role == Role.USER } ?: return
        if (index != messages.lastIndex) return
        val history = messages.take(index - 1)
        // 旧版本立刻让位给即将生成的新版本，避免流式期间同时出现两条回答
        _state.update { it.copy(messages = messages.take(index)) }
        viewModelScope.launch {
            if (messages[index].parentId == null) {
                repo.attachToParent(messages[index].id, prev.id)
            }
            sendText(
                text = prev.text,
                reuseUser = true,
                parentUserId = prev.id,
                historyOverride = history,
                attachments = prev.attachments,
            )
        }
    }

    /** 版本切换：delta = -1 上一版 / +1 下一版（提问版本与回答版本共用） */
    fun switchVersion(messageId: String, delta: Int) {
        if (_state.value.streaming) return
        val current = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        viewModelScope.launch {
            if (current.role == Role.USER) {
                repo.switchUserVersion(messageId, delta)
                return@launch
            }
            val parentId = current.parentId ?: return@launch
            val versions = repo.versions(parentId)
            val index = versions.indexOfFirst { it.id == messageId }
            if (index < 0) return@launch
            val target = versions.getOrNull(index + delta) ?: return@launch
            repo.activateVersion(parentId, target.id)
        }
    }

    fun feedback(messageId: String, value: Int) {
        _state.update { s ->
            val next = s.feedback.toMutableMap()
            if (next[messageId] == value) next.remove(messageId) else next[messageId] = value
            s.copy(feedback = next)
        }
    }

    /** 继续生成：把已生成内容作为 assistant 前缀重放本轮请求，结果追加到同一条消息。 */
    fun continueGeneration() {
        if (_state.value.streaming) return
        val messages = _state.value.messages
        val index = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (index < 0 || index != messages.lastIndex) return
        val last = messages[index]
        if (!ContinuePolicy.canContinue(last)) return
        val user = messages.getOrNull(index - 1)?.takeIf { it.role == Role.USER } ?: return
        val history = messages.take(index - 1)
        val signature = last.blocks.firstOrNull { it.isThinking }?.payload
        sendText(
            text = user.text,
            reuseUser = true,
            parentUserId = user.id,
            historyOverride = history,
            target = last,
            prefill = ContextAssembler.Prefill(
                text = last.text,
                thinking = last.thinking.takeIf { it.isNotBlank() },
                signature = signature,
            ),
            attachments = user.attachments,
        )
    }

    private fun sendText(
        text: String,
        reuseUser: Boolean = false,
        parentUserId: String? = null,
        historyOverride: List<UiMessage>? = null,
        target: UiMessage? = null,
        prefill: ContextAssembler.Prefill? = null,
        attachments: List<UiAttachment> = emptyList(),
        clearDrafts: Boolean = false,
        newUserParentId: String? = null,
    ) {
        sendJob = viewModelScope.launch {
            var assistantId: String? = target?.id
            val blocks = target?.blocks?.toMutableList() ?: mutableListOf<UiBlock>()
            val indexMap = mutableMapOf<Int, Int>()
            var usedTools = target?.usedTools ?: false
            var thinkingStartedAt: Long? = null
            var thinkingEndedAt: Long? = null
            val startedAt = System.currentTimeMillis()

            fun thinkingMs(): Long {
                val start = thinkingStartedAt ?: return 0
                val end = thinkingEndedAt ?: SystemClock.elapsedRealtime()
                return (end - start).coerceAtLeast(0)
            }

            try {
                var sessionId = _state.value.sessionId
                if (sessionId == null) {
                    val created = repo.createSession(_state.value.settings.model)
                    sessionId = created.id
                    // 把新对话页上手动调过的开关带到刚创建的会话里
                    sessionToggles[created.id] = sessionToggles.remove("")
                        ?: (_state.value.thinking to _state.value.search)
                    selectSession(created.id)
                    delay(60)
                }
                val sid = sessionId ?: return@launch
                val session = repo.session(sid) ?: return@launch
                val history = historyOverride ?: _state.value.messages
                val needTitle = session.title.isBlank()

                val userId = if (reuseUser) {
                    parentUserId
                } else {
                    repo.appendUserMessage(sid, text, attachments, newUserParentId)
                }
                if (target == null) {
                    val assistant = repo.appendAssistantPlaceholder(sid, session.model, userId)
                    assistantId = assistant.id
                }
                drafts.remove(sid)
                _state.update {
                    it.copy(
                        input = "",
                        attachments = if (clearDrafts) emptyList() else it.attachments,
                        streaming = true,
                        error = null,
                        live = UiMessage(
                            id = assistantId.orEmpty(),
                            role = Role.ASSISTANT,
                            status = MessageStatus.STREAMING,
                            model = session.model,
                            createdAt = if (target != null) target.createdAt else startedAt,
                            blocks = blocks.toList(),
                        ),
                    )
                }

                var requestAttachments = attachments
                var fileRetry = 0
                while (true) {
                // 每次重试前记下已产生的内容，失败时回滚，避免重复文本
                val baseline = blocks.toList()
                val body = container.assembler.buildRequest(
                    history = history,
                    question = text,
                    model = session.model,
                    thinking = _state.value.thinking,
                    search = _state.value.search,
                    reminder = ContextAssembler.reminder(),
                    enableTitle = needTitle && target == null,
                    prefill = prefill,
                    attachments = requestAttachments,
                )

                val hasFiles = ContextAssembler.hasFileBlocks(history, requestAttachments)
                try {
                api.stream(_state.value.settings.baseUrl, settingsStore.apiKey, body, hasFiles)
                    .collect { event ->
                    when (event) {
                        is StreamEvent.Started -> Unit

                        is StreamEvent.BlockStarted -> {
                            val block = when (val b = event.block) {
                                is StartBlock.Text -> UiBlock(
                                    id = UUID.randomUUID().toString(),
                                    ordinal = blocks.size,
                                    type = BlockType.TEXT,
                                    text = b.text,
                                )
                                is StartBlock.Thinking -> {
                                    if (thinkingStartedAt == null) {
                                        thinkingStartedAt = SystemClock.elapsedRealtime()
                                    }
                                    UiBlock(
                                        id = UUID.randomUUID().toString(),
                                        ordinal = blocks.size,
                                        type = BlockType.THINKING,
                                        text = b.thinking,
                                        payload = b.signature,
                                    )
                                }
                                is StartBlock.Tool -> {
                                    usedTools = true
                                    UiBlock(
                                        id = UUID.randomUUID().toString(),
                                        ordinal = blocks.size,
                                        type = BlockType.TOOL,
                                        text = "",
                                        payload = b.payload ?: b.type,
                                    )
                                }
                            }
                            indexMap[event.index] = blocks.size
                            blocks.add(block)
                            publish(assistantId, blocks, startedAt)
                        }

                        is StreamEvent.TextDelta ->
                            appendText(event.index, event.text, blocks, indexMap, assistantId, startedAt)

                        is StreamEvent.ThinkingDelta ->
                            appendText(event.index, event.text, blocks, indexMap, assistantId, startedAt)

                        is StreamEvent.SignatureDelta -> {
                            val i = indexMap[event.index] ?: return@collect
                            blocks[i] = blocks[i].copy(payload = (blocks[i].payload ?: "") + event.signature)
                        }

                        is StreamEvent.CitationDelta -> {
                            val i = indexMap[event.index] ?: return@collect
                            val current = blocks[i]
                            val next = current.citations.toMutableList()
                            if (next.none { it.url == event.url }) {
                                next += Citation(url = event.url, title = event.title)
                                blocks[i] = current.copy(citations = next)
                                publish(assistantId, blocks, startedAt)
                            }
                        }

                        is StreamEvent.BlockStopped -> {
                            val i = indexMap[event.index] ?: return@collect
                            blocks[i] = blocks[i].copy(closed = true)
                            if (blocks[i].type == BlockType.THINKING && thinkingEndedAt == null) {
                                thinkingEndedAt = SystemClock.elapsedRealtime()
                            }
                            publish(assistantId, blocks, startedAt)
                        }

                        is StreamEvent.TitleReceived -> {
                            if (needTitle) repo.renameSession(sid, event.title)
                        }

                        is StreamEvent.Finished -> {
                            // 不同协议/版本可能用 refusal / content_filter / blocked 表达拒答
                            val refused = event.stopReason?.lowercase() in setOf(
                                "refusal", "content_filter", "blocked", "safety",
                            )
                            val status = if (refused) MessageStatus.REFUSED else MessageStatus.DONE
                            // 用量以最后一次 message_delta 为准（docs 02 §12），不与 start 相加
                            val u = event.usage
                            if (u != null && assistantId != null) {
                                repo.recordUsage(
                                    messageId = assistantId,
                                    sessionId = sid,
                                    model = session.model,
                                    inputTokens = u.inputTokens,
                                    outputTokens = u.outputTokens,
                                    cacheReadTokens = u.cacheReadTokens,
                                    cacheCreationTokens = u.cacheCreationTokens,
                                )
                            }
                            finish(
                                assistantId = assistantId,
                                status = status,
                                stopReason = event.stopReason,
                                blocks = blocks,
                                usedTools = usedTools,
                                sessionId = sid,
                                thinkingMs = thinkingMs(),
                            )
                        }
                    }
                }
                break
                } catch (e: ApiException) {
                    // file_id 失效：补传后重发（最多 2 次），用户无感
                    if (fileRetry >= MAX_FILE_RETRIES || !isFileIdError(e)) throw e
                    val refreshed = refreshFileIds(requestAttachments, userId) ?: throw e
                    requestAttachments = refreshed
                    blocks.clear()
                    blocks.addAll(baseline)
                    fileRetry++
                }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    assistantId?.let {
                        repo.finishAssistant(
                            it, MessageStatus.STOPPED, "aborted", blocks, usedTools, thinkingMs(),
                        )
                    }
                }
                _state.update { it.copy(streaming = false, live = null) }
            } catch (e: ApiException) {
                withContext(NonCancellable) {
                    // 命中内容风控：按「拒答」处理，与官方一致地撤回已生成内容，只留柔和提示
                    persistFailure(
                        assistantId,
                        blocks,
                        friendly(e),
                        thinkingMs(),
                        status = if (e.kind == ApiException.Kind.CONTENT_RISK) {
                            MessageStatus.REFUSED
                        } else {
                            MessageStatus.FAILED
                        },
                    )
                }
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    // 非协议异常也只给本地化文案，避免把原始堆栈/ID 铺到界面
                    persistFailure(
                        assistantId,
                        blocks,
                        ErrorMessages.of(ApiException.Kind.UNKNOWN),
                        thinkingMs(),
                    )
                }
            }
            // 双刷新之一：每次请求结束后同步一次余额（左下角常显随之更新）
            syncBalanceQuietly()
        }
    }

    private suspend fun persistFailure(
        assistantId: String?,
        blocks: List<UiBlock>,
        message: String,
        thinkingMs: Long,
        status: MessageStatus = MessageStatus.FAILED,
    ) {
        if (assistantId == null) {
            _state.update { it.copy(streaming = false, live = null, error = message) }
            return
        }
        // 拒答：与正常结束一致，丢弃已下发的思考与正文，只保留工具块，界面只留一句柔和提示
        val persisted = if (status == MessageStatus.REFUSED) {
            blocks.filter { it.type == BlockType.TOOL }
        } else {
            blocks
        }
        repo.finishAssistant(assistantId, status, null, persisted, false, thinkingMs)
        if (status == MessageStatus.REFUSED) {
            repo.failAssistant(assistantId, status, "")
            _state.update { it.copy(streaming = false, live = null, error = null) }
        } else {
            repo.failAssistant(assistantId, status, message)
            _state.update { it.copy(streaming = false, live = null, error = message) }
        }
    }

    private suspend fun finish(
        assistantId: String?,
        status: MessageStatus,
        stopReason: String?,
        blocks: List<UiBlock>,
        usedTools: Boolean,
        sessionId: String,
        thinkingMs: Long,
    ) {
        val id = assistantId ?: return
        // 拒答：按协议丢弃已下发的思考与正文，只保留工具块
        val persisted = if (status == MessageStatus.REFUSED) {
            blocks.filter { it.type == BlockType.TOOL }
        } else {
            blocks
        }
        repo.finishAssistant(id, status, stopReason, persisted, usedTools, thinkingMs)
        if (status == MessageStatus.DONE) {
            val preview = blocks.filter { it.isText }.joinToString("") { it.text }
            repo.touchSession(sessionId, preview)
        }
        _state.update { it.copy(streaming = false, live = null) }
    }

    fun stop() {
        sendJob?.cancel()
    }

    private fun appendText(
        serverIndex: Int,
        delta: String,
        blocks: MutableList<UiBlock>,
        indexMap: Map<Int, Int>,
        assistantId: String?,
        startedAt: Long,
    ) {
        val i = indexMap[serverIndex] ?: return
        blocks[i] = blocks[i].copy(text = blocks[i].text + delta)
        publish(assistantId, blocks, startedAt)
    }

    /** 50 ms 合并一次 UI，避免每个 token 触发重组。 */
    private fun publish(assistantId: String?, blocks: List<UiBlock>, startedAt: Long) {
        val id = assistantId ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastPublish < 50) return
        lastPublish = now
        _state.update {
            it.copy(
                live = UiMessage(
                    id = id,
                    role = Role.ASSISTANT,
                    status = MessageStatus.STREAMING,
                    blocks = blocks.toList(),
                    createdAt = startedAt,
                ),
            )
        }
    }

    /** 服务端判定 file_id 失效：补传本地原件后重发 */
    private fun isFileIdError(e: ApiException): Boolean {
        if (e.status != 400 && e.status != 404) return false
        val message = e.message.lowercase()
        return message.contains("file")
    }

    /** 重新上传后返回新的附件列表；任一附件没有本地原件则放弃重试 */
    private suspend fun refreshFileIds(
        attachments: List<UiAttachment>,
        messageId: String?,
    ): List<UiAttachment>? {
        if (attachments.none { it.fileId != null }) return null
        var ok = true
        val updated = attachments.map { attachment ->
            val local = attachment.localUri
            if (attachment.fileId == null || local == null) {
                if (attachment.fileId != null) ok = false
                return@map attachment
            }
            val fileId = runCatching {
                uploadOne(
                    uri = Uri.parse(local),
                    name = attachment.name,
                    mime = attachment.mime,
                    isImage = attachment.isImage,
                    size = attachment.size,
                    onProgress = {},
                )
            }.getOrNull()
            if (fileId == null) {
                ok = false
                attachment
            } else {
                attachment.copy(fileId = fileId)
            }
        }
        if (!ok) return null
        if (messageId != null) {
            repo.updateAttachmentFileIds(messageId, updated.map { it.id to it.fileId })
        }
        return updated
    }

    private fun friendly(e: ApiException): String {
        Log.w(
            "ChatCat",
            "request failed kind=${e.kind} status=${e.status} request_id=${e.requestId} msg=${e.message}",
        )
        return ErrorMessages.of(e.kind)
    }

    class Factory(
        private val app: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(app, container) as T
    }
}
