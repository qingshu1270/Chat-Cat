package com.chatcat.ui.balance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.R
import com.chatcat.data.remote.BalanceInfo
import com.chatcat.ui.common.OfficialLinks
import com.chatcat.ui.common.openUrl
import com.chatcat.domain.model.PeakValley
import com.chatcat.ui.common.rememberTickingNow
import com.chatcat.ui.components.SecondaryPage
import kotlin.math.roundToInt
import kotlin.random.Random

/** 余额充足时的小狮子（4 张随机） */
private val MASCOT_MONEY = intArrayOf(
    R.drawable.mascot_money_1, R.drawable.mascot_money_2,
    R.drawable.mascot_money_3, R.drawable.mascot_money_4,
)

/** 低于预警线时的小狮子（2 张随机） */
private val MASCOT_NO_MONEY = intArrayOf(
    R.drawable.mascot_nomoney_1, R.drawable.mascot_nomoney_2,
)

/**
 * 余额页（二级页）：展示官方 GET /user/balance 的总余额 / 充值余额 / 赠金余额，
 * 并提供余额预警线设置与「余额状态」小狮子。
 */
@Composable
fun BalanceScreen(
    loading: Boolean,
    info: BalanceInfo?,
    available: Boolean,
    error: String?,
    warn: Float,
    onWarnChange: (Float) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val balance = info?.totalBalance?.toDoubleOrNull()
    // 低于预警线（或官方判定不可用）时走「余额不足」表情；余额未知时按充足处理
    val low = balance != null && (balance < warn || !available)

    SecondaryPage(title = "账户余额", onBack = onBack) {
        Spacer(Modifier.height(16.dp))

        when {
            loading -> CircularProgressIndicator()

            error != null -> Text(
                error,
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            info == null -> Text(
                "暂无余额信息",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                BalanceCard("总余额", info.totalBalance, info.currency, highlight = true)
                Spacer(Modifier.height(12.dp))
                BalanceCard("充值余额", info.toppedUpBalance, info.currency)
                Spacer(Modifier.height(12.dp))
                BalanceCard("赠金余额", info.grantedBalance, info.currency)
                if (!available) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "当前余额不足，请到开放平台充值后使用。",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clickable { openUrl(context, OfficialLinks.RECHARGE) },
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "去开放平台充值",
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        WarnLineCard(warn = warn, onChange = onWarnChange)

        Spacer(Modifier.height(12.dp))
        PeakValleyCard()

        if (info != null) {
            Spacer(Modifier.height(10.dp))
            BalanceMascot(low = low, onRecharge = { openUrl(context, OfficialLinks.RECHARGE) })
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "余额查询失败时可返回重试；数据来自开放平台，仅供参考。",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 峰谷提示：当前处于梁文峰 / 谷时间，并给出距离下次切换的倒计时。
 * 规则见 [com.chatcat.domain.model.PeakValley]（工作日 9-12、14-18 为峰，其余为谷）。
 */
@Composable
private fun PeakValleyCard() {
    val now = rememberTickingNow()
    val valley = PeakValley.isValley(now)
    val accent = if (valley) Color(0xFF2FA36B) else Color(0xFFE5484D)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (valley) "当前处于梁文谷时间" else "当前处于梁文峰时间",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = accent,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "距下次切换 ${PeakValley.countdown(now)}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 余额预警线：低于该值时聊天页余额变为提示色，这里换成「没余粮」的小狮子 */
@Composable
private fun WarnLineCard(warn: Float, onChange: (Float) -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "余额预警线",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "¥${warn.roundToInt()}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        WarnSlider(warn = warn, onValueChange = onChange)
        Text(
            "低于这个金额时，聊天页左下角的余额会变成醒目的提示色。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 仅调试页使用：滑条外观预览，不影响正式流程 */
@Composable
internal fun WarnSliderPreview() {
    var value by remember { mutableStateOf(30f) }
    WarnSlider(warn = value, onValueChange = { value = it })
}

/**
 * 余额预警线滑条：全圆角胶囊轨道 + 渐变填充 + 带描边的圆形滑块。
 * 自己画（不用 M3 Slider）是为了避开默认方角轨道与过细的滑块，和整体圆角风格统一。
 */
@Composable
private fun WarnSlider(warn: Float, onValueChange: (Float) -> Unit) {
    val min = 1f
    val max = 200f
    var widthPx by remember { mutableStateOf(0f) }
    val fraction = ((warn - min) / (max - min)).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val warm = Color(0xFFFFB74D)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    fun update(x: Float) {
        if (widthPx <= 0f) return
        val f = (x / widthPx).coerceIn(0f, 1f)
        val value = (min + f * (max - min)).roundToInt().toFloat()
        if (value != warn) onValueChange(value)
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { update(it.x) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        update(change.position.x)
                        change.consume()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                val trackH = 8.dp.toPx()
                val top = (size.height - trackH) / 2f
                val radius = CornerRadius(trackH / 2f, trackH / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackH),
                    cornerRadius = radius,
                )
                val filled = size.width * fraction
                if (filled > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent, warm)),
                        topLeft = Offset(0f, top),
                        size = Size(filled.coerceAtLeast(trackH), trackH),
                        cornerRadius = radius,
                    )
                }
                val thumbR = 10.dp.toPx()
                val cx = (size.width * fraction).coerceIn(thumbR, size.width - thumbR)
                val cy = size.height / 2f
                drawCircle(color = Color.White, radius = thumbR, center = Offset(cx, cy))
                drawCircle(
                    color = accent,
                    radius = thumbR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                "¥1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "¥200",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 余额状态小狮子：按充足/不足分组随机，进入页面时固定一张。
 * 小狮子下面常驻「给橘狮充饭卡」；余额低于预警线时，点小狮子本身也能直接去充值。
 */
@Composable
private fun BalanceMascot(low: Boolean, onRecharge: () -> Unit) {
    val pool = if (low) MASCOT_NO_MONEY else MASCOT_MONEY
    val image = remember(low) { pool[Random.nextInt(pool.size)] }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = if (low) "余额不足，点按去充值" else null,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable(enabled = low, onClick = onRecharge),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (low) "余额见底啦，记得充点余粮" else "余额充足，放心聊",
            style = MaterialTheme.typography.labelSmall,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onRecharge)
                .padding(horizontal = 22.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "给橘狮充饭卡",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun BalanceCard(
    label: String,
    amount: String,
    currency: String,
    highlight: Boolean = false,
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${amount.ifBlank { "--" }} ${currency}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 22.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
