package com.chatcat.ui.dev

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatcat.ui.balance.WarnSliderPreview
import com.chatcat.ui.components.MarkdownText
import com.chatcat.ui.components.MathBlock
import com.chatcat.ui.components.SecondaryPage

/**
 * 渲染自检页（仅 debug 包可见）。
 * 用来快速确认 Markdown / 公式 / 思考链样式，不影响正式版界面。
 */
@Composable
fun RenderProbePage(onBack: () -> Unit) {
    var browserUrl by remember { mutableStateOf<String?>(null) }
    val current = browserUrl
    if (current != null) {
        com.chatcat.ui.browser.BrowserPage(url = current, onClose = { browserUrl = null })
        return
    }
    SecondaryPage(title = "渲染自检", onBack = onBack) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                "打开内置浏览器",
                modifier = Modifier
                    .clickable { browserUrl = "https://example.com" }
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text("正文 Markdown", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            MarkdownText(
                "行内公式 \$E = mc^2\$ 与 \$a_i^2\$，粗体 **重点**，行内代码 `fun main()`。\n\n" +
                    "- 列表项 1\n- 列表项 2\n\n> 引用一段话\n\n" +
                    "## 二级标题\n\n普通段落，包含链接 [示例](https://example.com)。",
            )

            Spacer(Modifier.height(14.dp))
            Text("代码块配色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            MarkdownText(
                "```kotlin\n" +
                    "// 计算阶乘\n" +
                    "fun factorial(n: Int): Long {\n" +
                    "    val result = 1L\n" +
                    "    for (i in 2..n) result *= i\n" +
                    "    return result\n" +
                    "}\n" +
                    "val text = \"done\"\n" +
                    "```\n",
            )

            Spacer(Modifier.height(14.dp))
            Text("块级公式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            MathBlock("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
            MathBlock("\\sum_{i=1}^{n} i^2 = \\frac{n(n+1)(2n+1)}{6}")
            MathBlock("\\int_{0}^{\\infty} e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}")

            Spacer(Modifier.height(14.dp))
            Text("余额预警滑条", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            WarnSliderPreview()

            Spacer(Modifier.height(14.dp))
            Text("思考链样式（紧凑）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            MarkdownText(
                "先看 **题目条件**：\$a>0\$。\n- 第一步：求导\n- 第二步：代入 `x=1`",
                fontSize = 14.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                compact = true,
            )
        }
    }
}
