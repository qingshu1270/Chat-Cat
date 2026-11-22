package com.chatcat.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chatcat.domain.model.PeakValley
import kotlinx.coroutines.delay

/** 每 30 秒刷新一次的「当前时间」，用于峰谷提示 */
@Composable
fun rememberTickingNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** 峰：红色；谷：绿色。与余额页提示卡用同一组色值 */
private val PEAK_COLOR = Color(0xFFE5484D)
private val VALLEY_COLOR = Color(0xFF2FA36B)

/**
 * 峰谷标记：峰时段红色「峰」，谷时段绿色「谷」。
 *
 * 之前画的是 ∩ / ∪ 两条曲线，缩在 11sp 小字后面只有十几个像素，看不出凸还是凹；
 * 换成单字，一眼就能读出来。
 */
@Composable
fun PeakValleyTag(modifier: Modifier = Modifier) {
    val now = rememberTickingNow()
    val valley = PeakValley.isValley(now)
    Text(
        text = if (valley) "谷" else "峰",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = if (valley) VALLEY_COLOR else PEAK_COLOR,
    )
}

/** 余额页的峰谷提示行：当前处于梁文峰/谷时间 · 距下次切换 03:25 */
@Composable
fun rememberPeakValleyHint(): String {
    val now = rememberTickingNow()
    return remember(now) { PeakValley.hint(now) }
}
