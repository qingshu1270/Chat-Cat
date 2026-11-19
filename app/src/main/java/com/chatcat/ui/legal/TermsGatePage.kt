package com.chatcat.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.ui.common.openUrl
import com.chatcat.ui.components.SecondaryPage

/**
 * 首次填写 API Key 前强制阅读的协议页。
 *
 * 一次性接受：Chat Cat 用户协议 + DeepSeek 用户协议 + 开放平台服务协议。
 * 未勾选时「同意并继续」不可用，避免误触跳过。
 */
@Composable
fun TermsGatePage(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }

    SecondaryPage(title = "用户协议与免责声明", onBack = onReject, scrollable = false) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Text(
                    "开始使用前，请阅读并同意以下协议。本应用是第三方客户端，" +
                        "模型服务由 DeepSeek 开放平台提供，与 DeepSeek 官方无隶属关系。",
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(CHATCAT_TERMS) { (title, body) ->
                Column(Modifier.padding(bottom = 14.dp)) {
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
            item {
                Text(
                    "查看官方协议",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                AgreementLink("《DeepSeek 用户协议》") {
                    openUrl(context, DEEPSEEK_TERMS_URL)
                }
                AgreementLink("《DeepSeek 开放平台服务协议》") {
                    openUrl(context, DEEPSEEK_PLATFORM_TERMS_URL)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text(
                    "我已阅读并同意《Chat Cat 用户协议》《DeepSeek 用户协议》《DeepSeek 开放平台服务协议》",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { checked = !checked },
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            GatePrimaryButton(
                text = "同意并继续",
                enabled = checked,
                onClick = onAccept,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "不同意",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReject)
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GatePrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgreementLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
