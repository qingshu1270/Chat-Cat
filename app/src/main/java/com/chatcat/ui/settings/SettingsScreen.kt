package com.chatcat.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.BuildConfig
import com.chatcat.R
import com.chatcat.data.archive.ImportMode
import com.chatcat.data.archive.ImportPreview
import com.chatcat.data.prefs.AppSettings
import com.chatcat.security.Integrity
import com.chatcat.security.SignatureGuard
import com.chatcat.ui.chat.LinkImportStage
import com.chatcat.domain.model.ExportLinkParser
import com.chatcat.ui.chat.ApiAccount
import com.chatcat.ui.common.openUrl
import com.chatcat.ui.components.SecondaryPage
import com.chatcat.ui.theme.LocalDarkTheme
import com.chatcat.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TERMS_URL =
    "https://cdn.deepseek.com/policies/zh-CN/deepseek-open-platform-terms-of-service.html"

private sealed interface SettingsPage {
    data object Account : SettingsPage
    data object ApiKeys : SettingsPage
    data object Data : SettingsPage
    data object Language : SettingsPage
    data object Theme : SettingsPage
    data object FontSize : SettingsPage
    data object Personal : SettingsPage
    data object About : SettingsPage
    data object Terms : SettingsPage
    data object ChatCatTerms : SettingsPage
    data object ClearKey : SettingsPage
    data object DeleteAll : SettingsPage
    data object Advanced : SettingsPage
    data object Privacy : SettingsPage
    data object Licenses : SettingsPage
    data object Integrity : SettingsPage
    data object RenderProbe : SettingsPage
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    archiving: Boolean,
    archiveNotice: String?,
    importPreview: ImportPreview?,
    usageOverviewOpen: Boolean,
    usageOverview: com.chatcat.domain.model.UsageSummary?,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onFontScale: (Float) -> Unit,
    onDefaultThinking: (Boolean) -> Unit,
    onDefaultSearch: (Boolean) -> Unit,
    onThinkingDisplay: (String) -> Unit,
    onRenameUser: (String) -> Unit,
    onClearSessions: () -> Unit,
    onClearKeys: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onConfirmImport: (Boolean, ImportMode) -> Unit,
    onCancelImport: () -> Unit,
    onBaseUrl: (String) -> Unit,
    onOpenUsage: () -> Unit,
    onCloseUsage: () -> Unit,
    onOpenBalance: () -> Unit,
    apiAccounts: List<ApiAccount>,
    onSelectApiAccount: (String) -> Unit,
    onRenameApiAccount: (String, String) -> Unit,
    onDeleteApiAccount: (String) -> Unit,
    onAddApiAccount: (String, String) -> Unit,
    importGuideOpen: Boolean,
    linkStage: LinkImportStage,
    detectedLink: String?,
    linkPrompt: String?,
    downloadProgress: Float,
    linkError: String?,
    clipboardHint: String?,
    linkSuccess: String?,
    onOpenImportGuide: () -> Unit,
    onCloseImportGuide: () -> Unit,
    onCheckClipboard: () -> Unit,
    onSubmitLink: (String) -> Unit,
    onReadClipboardRaw: () -> String?,
    onFinishLinkImport: () -> Unit,
    onConfirmLinkImport: () -> Unit,
    onDismissLinkPrompt: () -> Unit,
    onRetryLinkImport: () -> Unit,
) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }

    // 设置主页响应系统返回；二级页由 SecondaryPage 自己的 BackHandler 处理
    BackHandler(enabled = page == null, onBack = onBack)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }
    val stamp = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(if (LocalDarkTheme.current) Tokens.BgDark else Tokens.BgLightSecondary)
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(
                            if (LocalDarkTheme.current) Tokens.BubbleDark else Color(0xFFEDEDED),
                            CircleShape,
                        )
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "返回",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "设置",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.size(36.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                SectionTitle("账户")
                SettingsCard {
                    SettingRow("账号管理", settings.displayName.ifBlank { "已配置" }) {
                        page = SettingsPage.Account
                    }
                    SettingRow("数据管理") {
                        page = SettingsPage.Data
                    }
                    SettingRow("用量统计") { onOpenUsage() }
                    SettingRow("查询余额") { onOpenBalance() }
                }

                SectionTitle("应用")
                SettingsCard {
                    SettingRow("语言", "中文（简体中文）") {
                        page = SettingsPage.Language
                    }
                    SettingRow("外观", themeLabel(settings.theme)) {
                        page = SettingsPage.Theme
                    }
                    SettingRow("字体大小", fontSizeLabel(settings.fontScale)) {
                        page = SettingsPage.FontSize
                    }
                    SettingRow("个性化") {
                        page = SettingsPage.Personal
                    }
                    SettingRow("高级设置", hostOf(settings.baseUrl)) {
                        page = SettingsPage.Advanced
                    }
                }

                SectionTitle("关于")
                SettingsCard {
                    SettingRow("版本", "v${BuildConfig.VERSION_NAME}") {
                        page = SettingsPage.About
                    }
                    SettingRow("服务协议") {
                        page = SettingsPage.Terms
                    }
                    SettingRow("隐私政策") {
                        page = SettingsPage.Privacy
                    }
                    SettingRow("应用签名校验") {
                        page = SettingsPage.Integrity
                    }
                    SettingRow("开源许可") {
                        page = SettingsPage.Licenses
                    }
                }

                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { page = SettingsPage.ClearKey }
                            .padding(horizontal = 16.dp, vertical = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "清除 API Key",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "第三方客户端 Chat Cat，与 DeepSeek 官方无隶属关系。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        when (page) {
            SettingsPage.Account -> AccountPage(
                displayName = settings.displayName,
                keySummary = apiAccounts.firstOrNull { it.current },
                onOpenKeys = { page = SettingsPage.ApiKeys },
                onRenameUser = onRenameUser,
                onBack = { page = null },
            )

            SettingsPage.ApiKeys -> ApiAccountsPage(
                accounts = apiAccounts,
                onSelect = onSelectApiAccount,
                onRename = onRenameApiAccount,
                onDelete = onDeleteApiAccount,
                onAdd = onAddApiAccount,
                onBack = { page = SettingsPage.Account },
            )

            SettingsPage.Data -> DataPage(
                archiving = archiving,
                notice = archiveNotice,
                onExport = { exportLauncher.launch("deepseek_data-$stamp.zip") },
                onImport = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                onOpenImportGuide = onOpenImportGuide,
                onDeleteAll = { page = SettingsPage.DeleteAll },
                onBack = { page = null },
            )

            SettingsPage.Language -> LanguagePage(onBack = { page = null })

            SettingsPage.Theme -> ThemePage(
                current = settings.theme,
                onSelect = onTheme,
                onBack = { page = null },
            )

            SettingsPage.FontSize -> FontSizePage(
                current = settings.fontScale,
                onSelect = onFontScale,
                onBack = { page = null },
            )

            SettingsPage.Personal -> PersonalPage(
                thinking = settings.thinking,
                search = settings.search,
                thinkingDisplay = settings.thinkingDisplay,
                onThinking = onDefaultThinking,
                onSearch = onDefaultSearch,
                onThinkingDisplay = onThinkingDisplay,
                onBack = { page = null },
            )

            SettingsPage.About -> AboutPage(
                onBack = { page = null },
                onOpenRenderProbe = { page = SettingsPage.RenderProbe },
            )

            SettingsPage.Terms -> TermsPage(
                onBack = { page = null },
                onOpenChatCatTerms = { page = SettingsPage.ChatCatTerms },
            )

            SettingsPage.ChatCatTerms -> ChatCatTermsPage(onBack = { page = null })

            SettingsPage.Advanced -> AdvancedPage(
                current = settings.baseUrl,
                onSave = onBaseUrl,
                onBack = { page = null },
            )

            SettingsPage.Privacy -> PrivacyPage(onBack = { page = null })

            SettingsPage.Licenses -> LicensesPage(onBack = { page = null })

            SettingsPage.Integrity -> IntegrityPage(onBack = { page = null })

            SettingsPage.RenderProbe -> com.chatcat.ui.dev.RenderProbePage(
                onBack = { page = null },
            )

            SettingsPage.ClearKey -> ClearKeyPage(
                onConfirm = {
                    page = null
                    onClearKeys()
                },
                onBack = { page = null },
            )

            SettingsPage.DeleteAll -> DeleteAllPage(
                onConfirm = {
                    page = null
                    onClearSessions()
                },
                onBack = { page = null },
            )

            null -> Unit
        }

        if (importGuideOpen) {
            ImportGuidePage(
                stage = linkStage,
                detectedLink = detectedLink,
                progress = downloadProgress,
                error = linkError,
                clipboardHint = clipboardHint,
                success = linkSuccess,
                onBack = onCloseImportGuide,
                onCheckClipboard = { onCheckClipboard() },
                onSubmitLink = onSubmitLink,
                onConfirmDetected = onConfirmLinkImport,
                onReadClipboardRaw = onReadClipboardRaw,
                onFinish = onFinishLinkImport,
                onRetry = onRetryLinkImport,
                onPickFile = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }

        // 检测到导出链接：确认后自动下载并导入（唯一的弹窗场景）
        if (importGuideOpen &&
            linkStage != LinkImportStage.DONE &&
            !linkPrompt.isNullOrBlank()
        ) {
            AlertDialog(
                onDismissRequest = onDismissLinkPrompt,
                title = { Text("检测到导出下载链接") },
                text = {
                    Column {
                        Text("是否下载该导出包？下载完成后会先显示备份内容，再由你选择合并增量或覆盖导入。")
                        Spacer(Modifier.height(10.dp))
                        // 只展示主机与文件名，不铺整条带签名的长链接
                        val meta = remember(linkPrompt) {
                            val uri = runCatching {
                                java.net.URI(linkPrompt.orEmpty())
                            }.getOrNull()
                            val host = uri?.host?.lowercase()
                            val name = uri?.path
                                ?.substringAfterLast('/')
                                ?.takeIf { it.contains('.') && it.length <= 60 }
                            Triple(host, name, host != null && ExportLinkParser.isTrustedHosting(host))
                        }
                        val host = meta.first
                        val fileName = meta.second
                        Text(
                            listOfNotNull(host, fileName).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        if (host != null && !ExportLinkParser.isOfficialHost(host)) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (meta.third) {
                                    "文件托管在对象存储上，链接带时效签名，过期后重新导出即可。"
                                } else {
                                    "该地址不在 deepseek.com 下，请确认是你刚从官方导出页复制的链接。"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (meta.third) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirmLinkImport) { Text("下载并导入") }
                },
                dismissButton = {
                    TextButton(onClick = onDismissLinkPrompt) { Text("暂不") }
                },
            )
        }

        if (usageOverviewOpen && usageOverview != null) {
            com.chatcat.ui.chat.UsageOverviewPage(
                usage = usageOverview,
                onBack = onCloseUsage,
            )
        }

        if (importPreview != null) {
            ImportReviewPage(
                preview = importPreview,
                archiving = archiving,
                onConfirm = onConfirmImport,
                onCancel = onCancelImport,
            )
        }
    }
}

@Composable
private fun AccountPage(
    displayName: String,
    keySummary: ApiAccount?,
    onOpenKeys: () -> Unit,
    onRenameUser: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(displayName) { mutableStateOf(displayName) }
    SecondaryPage(title = "账号管理", onBack = onBack) {
        Text(
            "本项目不使用 DeepSeek 账号登录，只使用你自己的 API Key。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            // 用户名可改：只保存在本机，用于侧边栏与账号页展示
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        draft = displayName
                        editing = true
                    }
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "用户名",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    displayName.ifBlank { "未设置" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "修改用户名",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InfoRow(
                "API 密钥",
                keySummary?.let { "${it.name} · ${it.masked}" } ?: "未配置",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenKeys)
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "管理 API 密钥",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "可保存多个 Key，命名后随时切换；切换立即生效，不需要重新输入。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("修改用户名") },
            text = {
                Column {
                    Text(
                        "只保存在本机，用于侧边栏与账号页展示，不影响 API 调用。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 24) draft = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${draft.length}/24",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameUser(draft)
                        editing = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DataPage(
    archiving: Boolean,
    notice: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenImportGuide: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPage(title = "数据管理", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        PageCard {
            // 只打开引导页、不动数据，因此不受 archiving 影响，避免导出入进行中时点了没反应
            SettingRow("从 DeepSeek 全量导入历史数据", "推荐") {
                onOpenImportGuide()
            }
        }
        Text(
            "从官方 App 获取导出链接，自动下载并导入，无需手动找文件。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PageCard {
            SettingRow("导入本地 ZIP 文件") { if (!archiving) onImport() }
        }
        Text(
            "降级方式：选择官方导出的 deepseek_data-*.zip（v3）。导入前会先预览，并可选合并增量或覆盖。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PageCard {
            SettingRow("导出所有历史对话") { if (!archiving) onExport() }
        }
        Text(
            "导出为官方 v3 ZIP：manifest.json + chat_sessions/*.json + 会话索引，可在官方网页端或本应用导入。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PageCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDeleteAll)
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "删除所有历史对话",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (archiving || notice != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (archiving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (archiving) "正在处理…" else notice.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LanguagePage(onBack: () -> Unit) {
    SecondaryPage(title = "语言", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        PageCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "简体中文",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
        }
        Text(
            "目前仅提供简体中文，多语言将在后续版本补充。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemePage(current: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    SecondaryPage(title = "外观", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        PageCard {
            ThemeOption("跟随系统", current == "system") { onSelect("system") }
            ThemeOption("浅色", current == "light") { onSelect("light") }
            ThemeOption("深色", current == "dark") { onSelect("dark") }
        }
    }
}

@Composable
private fun FontSizePage(
    current: Float,
    onSelect: (Float) -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPage(title = "字体大小", onBack = onBack) {
        var value by remember(current) { mutableStateOf(current) }
        Spacer(Modifier.height(16.dp))
        PageCard {
            Text(
                "字体大小",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "你可以拖动滑块来调整字体大小。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onSelect(value) },
                valueRange = AppSettings.FONT_SCALE_MIN..AppSettings.FONT_SCALE_MAX,
                steps = 7,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "预览文字大小",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "深度思考让模型在回答前先进行推理，适合复杂问题与数学计算。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PersonalPage(
    thinking: Boolean,
    search: Boolean,
    thinkingDisplay: String,
    onThinking: (Boolean) -> Unit,
    onSearch: (Boolean) -> Unit,
    onThinkingDisplay: (String) -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPage(title = "个性化", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        PageCard {
            SwitchRow("默认开启深度思考", thinking, onThinking)
            SwitchRow("默认开启智能搜索", search, onSearch)
        }
        Text(
            "仅作为新对话的默认开关，可在输入区随时切换。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "思考链展示",
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            ModeOption(
                title = "限高展开",
                badge = "默认",
                desc = "思考时展开一块限高区域，内容在其中流式滚动；结束后自动收起",
                selected = thinkingDisplay == AppSettings.THINKING_LIMITED,
            ) { onThinkingDisplay(AppSettings.THINKING_LIMITED) }
            ModeOption(
                title = "默认收起",
                desc = "只显示「已思考（用时 N 秒）」，点开才看思考过程",
                selected = thinkingDisplay == AppSettings.THINKING_COLLAPSED,
            ) { onThinkingDisplay(AppSettings.THINKING_COLLAPSED) }
            ModeOption(
                title = "展开全部",
                desc = "始终展开完整思考过程，与正文一起自然滚动",
                selected = thinkingDisplay == AppSettings.THINKING_FULL,
            ) { onThinkingDisplay(AppSettings.THINKING_FULL) }
        }
        Text(
            "手动点开/收起后，本条回答不再自动折叠。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit, onOpenRenderProbe: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SecondaryPage(title = "关于", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        // 置顶：作者与开源出处（按项目要求不给出具体仓库链接）
        PageCard {
            InfoRow("作者", "qingshu")
            InfoRow("开源平台", "GitHub · Gitee")
        }
        Text(
            "本项目开源发布于 GitHub 与 Gitee 开源平台，可在上述平台搜索「Chat Cat」获取源码。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PageCard {
            InfoRow("应用", BuildConfig.APP_DISPLAY_NAME)
            InfoRow("版本", "v${BuildConfig.VERSION_NAME}")
            InfoRow("服务来源", "DeepSeek 开放平台")
            InfoRow("开源协议", "GPL-3.0")
        }
        Text(
            "第三方客户端，与 DeepSeek 官方无隶属关系。内容由 AI 生成，请自行核实。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.DEBUG) {
            Text(
                "渲染自检（仅 beta）",
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .clickable(onClick = onOpenRenderProbe),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("查看 GPL-3.0 许可证") {
            com.chatcat.ui.common.openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html")
        }
        Text(
            "本项目以 GNU General Public License v3.0 发布：可自由使用、修改与再分发，衍生作品需以同一协议开源。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TermsPage(onBack: () -> Unit, onOpenChatCatTerms: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SecondaryPage(title = "服务协议", onBack = onBack) {
        Text(
            "本应用是第三方客户端，通过 DeepSeek 开放平台调用模型服务，与 DeepSeek 官方无隶属关系。" +
                "使用前请阅读并同意下列协议：",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "严禁利用本应用生成、传播违反法律法规的内容。因你的使用行为产生的责任由你自行承担。",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("《Chat Cat 用户协议》") { onOpenChatCatTerms() }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("《DeepSeek 用户协议》") {
            runCatching { openUrl(context, com.chatcat.ui.legal.DEEPSEEK_TERMS_URL) }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("《DeepSeek 开放平台服务协议》") {
            runCatching { openUrl(context, com.chatcat.ui.legal.DEEPSEEK_PLATFORM_TERMS_URL) }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "首次填写 API Key 时需一次性确认以上全部协议。",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatCatTermsPage(onBack: () -> Unit) {
    SecondaryPage(title = "Chat Cat 用户协议", onBack = onBack) {
        Text(
            "版本 v${com.chatcat.ui.legal.TERMS_VERSION}",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        com.chatcat.ui.legal.CHATCAT_TERMS.forEach { (title, body) ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ClearKeyPage(onConfirm: () -> Unit, onBack: () -> Unit) {
    SecondaryPage(title = "清除 API Key", onBack = onBack) {
        Text(
            "清除后需要重新填写 Key 才能继续对话，聊天记录不会删除。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DangerButton("清除 API Key", onConfirm)
    }
}

@Composable
private fun DeleteAllPage(onConfirm: () -> Unit, onBack: () -> Unit) {
    SecondaryPage(title = "删除所有历史对话", onBack = onBack) {
        Text(
            "所有会话与消息将被删除，且无法恢复。API Key 与设置不受影响。建议先导出备份。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DangerButton("删除所有历史对话", onConfirm)
    }
}

@Composable
private fun ImportReviewPage(
    preview: ImportPreview,
    archiving: Boolean,
    onConfirm: (Boolean, ImportMode) -> Unit,
    onCancel: () -> Unit,
) {
    var keepName by remember(preview) { mutableStateOf(false) }
    // 默认合并增量：不动本机已有对话，只补新增，符合「导入历史」的直觉
    var mode by remember(preview) { mutableStateOf(ImportMode.MERGE) }

    SecondaryPage(title = "导入历史对话", onBack = onCancel) {
        Text(
            "请确认备份内容，并选择导入方式。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PageCard {
            InfoRow("备份格式", preview.formatLabel)
            InfoRow("对话数量", "${preview.sessionCount}")
            InfoRow("消息数量", "${preview.messageCount}")
        }

        Text(
            "导入方式",
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            ModeOption(
                title = "合并增量",
                badge = "推荐",
                desc = "保留本机已有对话，只补上备份里没有的；同一份备份重复导入不会产生副本",
                selected = mode == ImportMode.MERGE,
            ) { mode = ImportMode.MERGE }
            ModeOption(
                title = "覆盖本机对话",
                desc = "清空本机全部对话后整体替换，无法撤销；建议先导出备份",
                selected = mode == ImportMode.REPLACE,
            ) { mode = ImportMode.REPLACE }
        }

        if (preview.userName != null) {
            Text(
                "备份中包含用户信息",
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PageCard {
                SwitchRow("保留用户名「${preview.userName}」", keepName) { keepName = it }
            }
            Text(
                "仅保存显示名，用于侧边栏与账号管理展示；邮箱、手机号等其他信息一律不读取。不保留则完全忽略。",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "备份中未包含可识别的用户名。",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = when {
                archiving -> "正在导入…"
                mode == ImportMode.MERGE -> "开始合并"
                else -> "覆盖导入"
            },
            enabled = !archiving,
        ) { onConfirm(preview.userName != null && keepName, mode) }
        Spacer(Modifier.height(10.dp))
        Text(
            "取消导入",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !archiving, onClick = onCancel)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 导入方式单选行：整行可点，避免只点中那个小圆点 */
@Composable
private fun ModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hostOf(baseUrl: String): String =
    baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

@Composable
private fun AdvancedPage(
    current: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var value by remember(current) { mutableStateOf(current) }
    val valid = value.startsWith("https://") || value.startsWith("http://")

    SecondaryPage(title = "高级设置", onBack = onBack) {
        Text(
            "API 地址。默认 https://api.deepseek.com；使用第三方中转服务时填写其兼容地址，" +
                "请自行确认对方可信，Key 会随请求发送到该地址。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            BasicTextField(
                value = value,
                onValueChange = { value = it.trim() },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            )
        }
        if (!valid) {
            Text(
                "地址需以 http:// 或 https:// 开头",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("保存", enabled = valid) {
            onSave(value.trimEnd('/'))
            onBack()
        }
        Text(
            "恢复默认地址",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { value = AppSettings.DEFAULT_BASE_URL }
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyPage(onBack: () -> Unit) {
    SecondaryPage(title = "隐私政策", onBack = onBack) {
        Text(
            "本应用为本地客户端，不运营服务器。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        PrivacyItem("API Key", "由系统加密存储（EncryptedSharedPreferences），仅用于向你配置的服务地址发起请求，不会上传到任何第三方。")
        PrivacyItem("对话内容", "保存在本机数据库；发送消息时会按你的配置发送到 DeepSeek 开放平台接口，用于生成回答。")
        PrivacyItem("附件", "上传到你配置的服务地址换取 file_id；原始文件仅保留本机路径，不会备份到云端。")
        PrivacyItem("日志", "仅记录错误类型与 request_id 用于排查，不包含对话正文与 Key。")
        Text(
            "导出备份会包含全部对话文本与附件元信息，请自行妥善保管；如需彻底删除，可在「数据管理」中删除全部对话。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyItem(title: String, body: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 校验通过时随机展示的守护插画（18 张） */
private val INTEGRITY_OK_IMAGES = intArrayOf(
    R.drawable.integrity_ok_1, R.drawable.integrity_ok_2, R.drawable.integrity_ok_3,
    R.drawable.integrity_ok_4, R.drawable.integrity_ok_5, R.drawable.integrity_ok_6,
    R.drawable.integrity_ok_7, R.drawable.integrity_ok_8, R.drawable.integrity_ok_9,
    R.drawable.integrity_ok_10, R.drawable.integrity_ok_11, R.drawable.integrity_ok_12,
    R.drawable.integrity_ok_13, R.drawable.integrity_ok_14, R.drawable.integrity_ok_15,
    R.drawable.integrity_ok_16, R.drawable.integrity_ok_17, R.drawable.integrity_ok_18,
)

/** 校验失败时随机展示的警示插画（9 张） */
private val INTEGRITY_NO_IMAGES = intArrayOf(
    R.drawable.integrity_no_1, R.drawable.integrity_no_2, R.drawable.integrity_no_3,
    R.drawable.integrity_no_4, R.drawable.integrity_no_5, R.drawable.integrity_no_6,
    R.drawable.integrity_no_7, R.drawable.integrity_no_8, R.drawable.integrity_no_9,
)

@Composable
private fun IntegrityPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val integrity = remember { SignatureGuard.check(context) }

    val ok = integrity == Integrity.OFFICIAL || integrity == Integrity.BETA
    val mascot = remember(integrity) {
        val pool = if (ok) INTEGRITY_OK_IMAGES else INTEGRITY_NO_IMAGES
        pool[kotlin.random.Random.nextInt(pool.size)]
    }

    SecondaryPage(title = "应用签名校验", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(mascot),
                contentDescription = null,
                modifier = Modifier.size(132.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (integrity) {
                    Integrity.OFFICIAL -> "签名验证通过"
                    Integrity.BETA -> "开发者签名（beta 包）"
                    Integrity.TAMPERED -> "签名验证未通过"
                    Integrity.UNKNOWN -> "无法读取签名"
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            if (ok) {
                "签名验证通过，但由于本项目是开源项目，请确保您的应用来源于 Chat Cat 官方开源项目，" +
                    "目前仅在 GitHub 和 Gitee 进行开源；"
            } else {
                "签名验证未通过：当前安装包与官方发布签名不一致，可能被二次打包或篡改。" +
                    "请立即停止输入 API Key，并前往 GitHub 或 Gitee 获取免费正版安装包。"
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )

        PageCard {
            InfoRow(
                "状态",
                when (integrity) {
                    Integrity.OFFICIAL -> "正版"
                    Integrity.BETA -> "测试版"
                    Integrity.TAMPERED -> "签名异常"
                    Integrity.UNKNOWN -> "无法确认"
                },
            )
        }
        Text(
            "校验在手机本地完成，不联网，也不会上传你的任何信息。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "导入历史数据、充值、填写 API Key 时会自动校验；发现异常将暂停该操作。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicensesPage(onBack: () -> Unit) {
    SecondaryPage(title = "开源许可", onBack = onBack) {
        Spacer(Modifier.height(10.dp))
        // 置顶：本项目的参考实现，MIT 协议全文随应用展示
        Text(
            "参考项目",
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            InfoRow("chat-demo（DeepSeek 官方开源前端）", "MIT")
        }
        Text(
            "本项目的对话交互、SSE 事件处理与 API 协议实现参考了 chat-demo，按 MIT 协议保留其版权声明与许可文本。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                // 原文按 80 列硬换行，直接铺在窄屏上会参差不齐；按段落合并成自然换行
                CHAT_DEMO_LICENSE.split("\n\n").joinToString("\n\n") { it.replace("\n", " ") },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "依赖组件",
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PageCard {
            InfoRow("Kotlin / Coroutines", "Apache-2.0")
            InfoRow("Jetpack Compose", "Apache-2.0")
            InfoRow("AndroidX Room / DataStore", "Apache-2.0")
            InfoRow("OkHttp / Okio", "Apache-2.0")
            InfoRow("Kotlinx Serialization", "Apache-2.0")
            InfoRow("AndroidX Browser", "Apache-2.0")
        }
        Text(
            "以上组件按 Apache License 2.0 授权使用；完整许可文本见 https://www.apache.org/licenses/LICENSE-2.0。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Chat Cat 本身以 GNU General Public License v3.0 发布，完整文本见项目根目录 LICENSE。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** chat-demo（DeepSeek 官方开源前端）的 MIT 许可全文，与 ref/chat-demo/LICENSE 一致 */
private const val CHAT_DEMO_LICENSE = """MIT License

Copyright (c) 2026 DeepSeek

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE."""

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun PageCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showChevron) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ThemeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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
private fun DangerButton(text: String, onClick: () -> Unit) {
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

private fun themeLabel(theme: String): String = when (theme) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun fontSizeLabel(scale: Float): String = when {
    scale <= 0.9f -> "小"
    scale >= 1.35f -> "特大"
    scale >= 1.15f -> "大"
    else -> "标准"
}
