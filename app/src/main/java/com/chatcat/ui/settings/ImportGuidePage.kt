package com.chatcat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chatcat.ui.chat.LinkImportStage
import com.chatcat.ui.common.OfficialLinks
import com.chatcat.ui.common.isOfficialAppInstalled
import com.chatcat.ui.common.openOfficialApp
import com.chatcat.ui.common.openUrl
import com.chatcat.ui.components.SecondaryPage
import com.chatcat.ui.theme.LocalDarkTheme
import com.chatcat.ui.theme.Tokens
import kotlinx.coroutines.delay

/**
 * 「从 DeepSeek 全量导入历史数据」引导页（默认模式）。
 *
 * 链路：打开官方网页 / App → 设置 → 数据管理 → 导出所有历史对话 → 复制下载链接
 * → 回到本页自动读取剪贴板 → 弹窗确认 → 下载 ZIP → 导入。
 *
 * 三层兜底同时存在：
 * 1. 回到前台自动重试读剪贴板（Android 10+ 要求前台焦点，首次常读不到）；
 * 2. 手动粘贴链接（剪贴板被系统拒绝时仍可用）；
 * 3. 上传 ZIP 文件（降级模式）。
 */
@Composable
fun ImportGuidePage(
    stage: LinkImportStage,
    detectedLink: String?,
    progress: Float,
    error: String?,
    clipboardHint: String?,
    success: String?,
    onBack: () -> Unit,
    onCheckClipboard: () -> Unit,
    onSubmitLink: (String) -> Unit,
    onConfirmDetected: () -> Unit,
    onReadClipboardRaw: () -> String?,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    val installed = remember { isOfficialAppInstalled(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }

    // 从官方 App / 浏览器切回来时系统才允许读剪贴板
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        delay(200)
        onCheckClipboard()
    }
    // 进入页面先探一次：用户可能已经复制好链接才进来
    LaunchedEffect(Unit) {
        delay(300)
        onCheckClipboard()
    }

    SecondaryPage(title = "从 DeepSeek 全量导入", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        Text(
            "从 DeepSeek 全量导入历史数据",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "在官方侧导出后复制下载链接，回到本页自动识别，不用手动找文件。",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        StepCard(1, "打开官方入口并登录") {
            Text(
                "官方导出入口在网页版：设置 → 数据管理 → 导出所有历史对话。" +
                    "App 内若没有该入口，请直接用网页版导出。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            GuideButton(
                text = "打开网页版 chat.deepseek.com",
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                primary = true,
            ) {
                openUrl(context, OfficialLinks.WEB_CHAT)
            }
            Spacer(Modifier.height(8.dp))
            GuideButton(
                text = if (installed) "打开 DeepSeek App" else "未安装官方 App，用上面的网页版",
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                primary = false,
                enabled = installed,
            ) {
                if (!openOfficialApp(context)) openUrl(context, OfficialLinks.HOME)
            }
        }

        StepCard(2, "导出并复制下载链接") {
            PathRow("设置", "数据管理")
            PathRow("导出所有历史对话", "获取下载链接 → 复制")
            Spacer(Modifier.height(8.dp))
            Text(
                "导出可能需要几十秒；生成后先「复制链接」再返回本页。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StepCard(3, "回到 Chat Cat 自动识别") {
            Text(
                "返回本页后会自动读取剪贴板中的下载链接并询问是否导入；系统禁止读剪贴板时，可在下方手动粘贴。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        StatusCard(
            stage = stage,
            detectedLink = detectedLink,
            progress = progress,
            error = error,
            clipboardHint = clipboardHint,
            success = success,
            onCheck = onCheckClipboard,
            onSubmitLink = onSubmitLink,
            onConfirmDetected = onConfirmDetected,
            onReadClipboardRaw = onReadClipboardRaw,
            onFinish = onFinish,
            onRetry = onRetry,
        )

        if (stage != LinkImportStage.DONE) {
            Spacer(Modifier.height(10.dp))
            GuideButton(
                text = if (stage == LinkImportStage.FAILED) "改为上传 ZIP 文件" else "无法自动导入？上传 ZIP 文件",
                icon = Icons.Outlined.UploadFile,
                primary = false,
                enabled = stage != LinkImportStage.DOWNLOADING && stage != LinkImportStage.IMPORTING,
                onClick = onPickFile,
            )
            Text(
                "降级模式：手动选择官方导出的 deepseek_data-*.zip，同样支持全量导入。",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "下载后会先显示备份内容，由你选择合并增量或覆盖。ZIP 只在本机缓存，导入结束立即删除。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PathRow(from: String, to: String) {
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            from,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "  →  ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            to,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusCard(
    stage: LinkImportStage,
    detectedLink: String?,
    progress: Float,
    error: String?,
    clipboardHint: String?,
    success: String?,
    onCheck: () -> Unit,
    onSubmitLink: (String) -> Unit,
    onConfirmDetected: () -> Unit,
    onReadClipboardRaw: () -> String?,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = if (LocalDarkTheme.current) Tokens.BubbleDark else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (stage) {
                LinkImportStage.IDLE, LinkImportStage.WAITING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (detectedLink != null) "已识别到导出链接" else "正在等待剪贴板中的导出链接…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (detectedLink != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            detectedLink,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (clipboardHint != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            clipboardHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (detectedLink != null) {
                        GuideButton(
                            text = "下载并导入该链接",
                            icon = Icons.Outlined.CloudDownload,
                            primary = true,
                            onClick = onConfirmDetected,
                        )
                        Spacer(Modifier.height(8.dp))
                        GuideButton(
                            text = "重新检测剪贴板",
                            icon = Icons.Outlined.CheckCircle,
                            primary = false,
                            onClick = onCheck,
                        )
                    } else {
                        GuideButton(
                            text = "我已复制链接，立即检测",
                            icon = Icons.Outlined.CheckCircle,
                            primary = true,
                            onClick = onCheck,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    ManualLinkInput(onSubmitLink = onSubmitLink, onReadClipboardRaw = onReadClipboardRaw)
                }

                LinkImportStage.DOWNLOADING -> {
                    Text(
                        "正在下载导出包 ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "文件较大时可能需要一两分钟，请保持应用在前台。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinkImportStage.IMPORTING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "正在导入历史对话，请勿退出…",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }

                LinkImportStage.DONE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            success ?: "导入完成",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GuideButton(
                        text = "完成",
                        icon = Icons.Outlined.CheckCircle,
                        primary = true,
                        onClick = onFinish,
                    )
                }

                LinkImportStage.FAILED -> {
                    Text(
                        error ?: "导入没有完成，请重试或改为上传 ZIP 文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            GuideButton(
                                text = "重试",
                                icon = Icons.Outlined.CloudDownload,
                                primary = true,
                                onClick = onRetry,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            GuideButton(
                                text = "重新检测",
                                icon = Icons.Outlined.CheckCircle,
                                primary = false,
                                onClick = onCheck,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 手动粘贴链接：剪贴板被系统拒绝时仍能完成导入 */
@Composable
private fun ManualLinkInput(
    onSubmitLink: (String) -> Unit,
    onReadClipboardRaw: () -> String?,
) {
    var link by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    Text(
        "或手动粘贴下载链接",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = link,
        onValueChange = { link = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("https://…（导出后复制的下载地址）", fontSize = 13.sp) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        shape = RoundedCornerShape(10.dp),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            GuideButton(
                text = "粘贴剪贴板内容",
                icon = Icons.Outlined.CloudDownload,
                primary = false,
            ) {
                val raw = onReadClipboardRaw()
                if (raw.isNullOrBlank()) {
                    hint = "系统未放行剪贴板，请长按输入框手动粘贴"
                } else {
                    link = raw.trim()
                    hint = null
                }
            }
        }
        Box(Modifier.weight(1f)) {
            GuideButton(
                text = "用此链接导入",
                icon = Icons.Outlined.CheckCircle,
                primary = false,
            ) {
                onSubmitLink(link)
            }
        }
    }
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            hint.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun GuideButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        primary -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        border = if (!primary && enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
    ) {
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
