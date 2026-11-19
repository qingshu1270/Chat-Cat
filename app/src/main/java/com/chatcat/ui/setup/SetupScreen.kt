package com.chatcat.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chatcat.BuildConfig
import com.chatcat.ui.common.OfficialLinks
import com.chatcat.ui.common.openUrl
import com.chatcat.ui.components.SecondaryPage
import kotlinx.coroutines.delay

/**
 * API 配置二级页：SecondaryPage 自身已是可滚动容器，内部只放表单内容，
 * 不再嵌套 fillMaxSize + verticalScroll（会触发 Compose 无限高度约束崩溃）。
 */
@Composable
fun ApiSetupPage(
    verifying: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onClipboardKey: () -> String? = { null },
) {
    SecondaryPage(title = "接入 API", onBack = onBack) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SetupFormContent(
                verifying = verifying,
                error = error,
                onSubmit = onSubmit,
                onClipboardKey = onClipboardKey,
                topSpacer = 8.dp,
            )
        }
    }
}

/** 独立整屏版本（保留兼容，若别处直接调用） */
@Composable
fun SetupScreen(
    verifying: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        SetupFormContent(
            verifying = verifying,
            error = error,
            onSubmit = onSubmit,
            onClipboardKey = { null },
            topSpacer = 64.dp,
        )
    }
}

/** 纯表单内容：只含 Spacer / fillMaxWidth 控件，可被任意滚动容器包裹 */
@Composable
private fun SetupFormContent(
    verifying: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onClipboardKey: () -> String?,
    topSpacer: Dp,
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var pasteHint by remember { mutableStateOf<String?>(null) }
    var pendingClipKey by remember { mutableStateOf<String?>(null) }

    // 从开放平台「复制」Key 后回到应用：自动探测剪贴板，弹窗询问是否填入
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        delay(350)
        if (key.isBlank()) onClipboardKey()?.let { pendingClipKey = it }
    }
    // 进入配置页先探一次：用户可能先在开放平台复制了 Key 才点进来
    LaunchedEffect(Unit) {
        delay(400)
        if (key.isBlank()) onClipboardKey()?.let { pendingClipKey = it }
    }

    Spacer(Modifier.height(topSpacer))
    Text(
        BuildConfig.APP_DISPLAY_NAME,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "使用你自己的 DeepSeek API Key 开始对话。Key 只保存在本机，不会上传。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(18.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "还没有 Key？三步接入",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(10.dp))
            StepLine("1", "到开放平台充值余额")
            StepLine("2", "创建 API Key 并复制")
            StepLine("3", "回到 Chat Cat 粘贴（会自动识别剪贴板）")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkButton("去充值", Modifier.weight(1f)) {
                    openUrl(context, OfficialLinks.RECHARGE)
                }
                LinkButton("创建 API Key", Modifier.weight(1f)) {
                    openUrl(context, OfficialLinks.API_KEYS)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "充值、开票、Key 管理都在官方开放平台完成，Chat Cat 不接触你的支付信息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = key,
        onValueChange = {
            key = it.trim()
            pasteHint = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Key") },
        placeholder = { Text("sk-...") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(
                onClick = {
                    val pasted = onClipboardKey()
                    if (pasted != null) {
                        key = pasted
                        pasteHint = null
                    } else {
                        pasteHint = "剪贴板里没有找到 sk- 开头的 Key"
                    }
                },
            ) { Text("粘贴") }
        },
    )
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (pasteHint != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = pasteHint.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { onSubmit(key) },
        enabled = key.isNotBlank() && !verifying,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (verifying) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("验证并开始")
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "Key 以密文保存在本机（Android Keystore），仅随请求发送给你配置的 API 地址。",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "第三方客户端，与 DeepSeek 官方无隶属关系。内容由 AI 生成，请自行核实；" +
            "严禁利用本应用生成、传播违反法律法规的内容。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(40.dp))

    if (pendingClipKey != null) {
        AlertDialog(
            onDismissRequest = { pendingClipKey = null },
            title = { Text("检测到剪贴板中的 API Key") },
            text = {
                Column {
                    Text("是否填入刚复制的 Key？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        maskKey(pendingClipKey.orEmpty()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        key = pendingClipKey.orEmpty()
                        pendingClipKey = null
                        pasteHint = null
                    },
                ) { Text("填入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClipKey = null }) { Text("忽略") }
            },
        )
    }
}

@Composable
private fun StepLine(index: String, text: String) {
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 只展示首尾，避免整串 Key 暴露在屏幕上 */
private fun maskKey(value: String): String = when {
    value.length <= 10 -> value
    else -> value.take(6) + "…" + value.takeLast(4)
}
