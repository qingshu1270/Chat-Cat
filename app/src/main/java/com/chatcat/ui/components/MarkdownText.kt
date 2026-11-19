package com.chatcat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.chatcat.domain.model.SyntaxHighlight
import com.chatcat.domain.model.TableCsv
import com.chatcat.domain.model.ToolSource
import com.chatcat.ui.theme.LocalDarkTheme
import com.chatcat.ui.theme.Tokens

private sealed interface Segment {
    data class Code(val language: String, val code: String) : Segment
    data class Markdown(val text: String) : Segment

    /** 块级公式（$$...$$ 或 \[...\]） */
    data class Math(val latex: String) : Segment
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Numbered(val number: String, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block

    /** `---` / `***` / `___` 分隔线 */
    data object Divider : Block
}

/**
 * 轻量 Markdown 渲染：代码块 / 标题 / 列表 / 引用 / 行内粗体·代码·链接。
 * 段落合并连续行，列表带悬挂缩进；未闭合围栏按普通文本处理，避免流式闪烁。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    /** 正文字号；Unspecified 时用主题默认 */
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    /** 紧凑模式：段落间距收紧、标题按正文字号处理（思考链内使用） */
    compact: Boolean = false,
    /** 正文保护：聊天页正文里的链接不响应点击，只在「选择文本」页里可交互 */
    linksClickable: Boolean = true,
    /** 公式颜色；未指定时跟随正文，思考链里传灰色保持统一 */
    mathColor: Color = Color.Unspecified,
    /** 公式基准字号；未指定时用 17sp（比正文略大一点，官方观感） */
    mathSize: TextUnit = TextUnit.Unspecified,
    /** 本轮来源列表：用于把模型自带的角标统一改成本轮 1..N 的序号 */
    sources: List<ToolSource> = emptyList(),
) {
    // 模型有时会在正文里直接写 [440](url) 这类角标，且常常出现在行首，
    // 会把「### 标题」「- 列表」挤到行中导致 Markdown 失效。这里先做统一归一化。
    val normalized = remember(text, sources) { normalizeCitations(text, sources) }
    val segments = remember(normalized) { splitSegments(normalized) }
    val base = MaterialTheme.typography.bodyLarge
    val bodyStyle = base.copy(
        fontSize = if (fontSize.isSpecified) fontSize else base.fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight else base.lineHeight,
    )
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val gap = if (compact) 0.55f else 1f
    Column(modifier) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Code -> {
                    CodeBlock(segment.language, segment.code)
                    Spacer(Modifier.height(12.dp * gap))
                }
                is Segment.Math -> MathBlock(
                    latex = segment.latex,
                    color = if (mathColor != Color.Unspecified) mathColor else textColor,
                    fontSize = if (mathSize.isSpecified) mathSize else 17.sp,
                )
                is Segment.Markdown -> MarkdownBlocks(segment.text, bodyStyle, textColor, gap, compact, linksClickable)
            }
        }
    }
}

@Composable
private fun MarkdownBlocks(
    markdown: String,
    bodyStyle: TextStyle,
    textColor: Color,
    gap: Float,
    compact: Boolean,
    linksClickable: Boolean,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> {
                    Spacer(Modifier.height((if (block.level == 1) 14.dp else 10.dp) * gap))
                    Text(
                        text = inline(block.text, MaterialTheme.colorScheme.primary, linksClickable, codeColor),
                        style = if (compact) {
                            bodyStyle.copy(fontSize = bodyStyle.fontSize * 1.05f, fontWeight = FontWeight.SemiBold)
                        } else {
                            bodyStyle.copy(
                                fontSize = when (block.level) {
                                    1 -> 21.sp
                                    2 -> 19.sp
                                    else -> 17.sp
                                },
                                lineHeight = when (block.level) {
                                    1 -> 30.sp
                                    2 -> 28.sp
                                    else -> 26.sp
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        color = textColor,
                    )
                    Spacer(Modifier.height(8.dp * gap))
                }

                is Block.Paragraph -> {
                    Text(
                        text = inline(block.text, MaterialTheme.colorScheme.primary, linksClickable, codeColor),
                        style = bodyStyle,
                        color = textColor,
                    )
                    Spacer(Modifier.height(14.dp * gap))
                }

                is Block.Bullet -> {
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .padding(top = 11.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inline(block.text, MaterialTheme.colorScheme.primary, linksClickable, codeColor),
                            style = bodyStyle,
                            color = textColor,
                        )
                    }
                    Spacer(Modifier.height(10.dp * gap))
                }

                is Block.Numbered -> {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = block.number,
                            modifier = Modifier.width(24.dp),
                            style = bodyStyle,
                            color = textColor,
                        )
                        Text(
                            text = inline(block.text, MaterialTheme.colorScheme.primary, linksClickable, codeColor),
                            style = bodyStyle,
                            color = textColor,
                        )
                    }
                    Spacer(Modifier.height(10.dp * gap))
                }

                is Block.Table -> {
                    TableBlock(block)
                    Spacer(Modifier.height(12.dp * gap))
                }

                is Block.Divider -> {
                    Spacer(Modifier.height(6.dp * gap))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(10.dp * gap))
                }

                is Block.Quote -> {
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inline(block.text, MaterialTheme.colorScheme.primary, linksClickable, codeColor),
                            style = bodyStyle,
                            color = textColor.copy(alpha = 0.75f),
                        )
                    }
                    Spacer(Modifier.height(10.dp * gap))
                }
            }
        }
    }
}

@Composable
private fun TableBlock(table: Block.Table) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val widths = (0 until columns).map { column ->
        val chars = (table.header.getOrNull(column)?.length ?: 0)
            .coerceAtLeast(table.rows.maxOfOrNull { it.getOrNull(column)?.length ?: 0 } ?: 0)
        (chars.coerceIn(4, 22) * 9 + 22).dp
    }
    Column {
        Text(
            text = if (copied) "已复制 CSV" else "复制为 CSV",
            modifier = Modifier
                .padding(bottom = 4.dp)
                .clickable {
                    clipboard.setText(AnnotatedString(TableCsv.encode(table.header, table.rows)))
                    copied = true
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp),
                    ),
            ) {
                TableRow(table.header, widths, columns, header = true)
                table.rows.forEach { row ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    TableRow(row, widths, columns, header = false)
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Dp>,
    columns: Int,
    header: Boolean,
) {
    Row(
        Modifier.background(
            if (header) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        ),
    ) {
        repeat(columns) { column ->
            Box(Modifier.width(widths[column]).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = inline(
                        cells.getOrNull(column).orEmpty(),
                        MaterialTheme.colorScheme.primary,
                        codeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 代码块配色：关键字 / 字符串 / 注释 / 数字 / 类型 / 函数调用各自一色。
 * 浅色与深色各一套，避免单一色相糊成一片。
 */
private fun highlightedCode(code: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    val keyword = if (dark) Color(0xFFC792EA) else Color(0xFFA626A4)
    val string = if (dark) Color(0xFFC3E88D) else Color(0xFF3F8F4A)
    val comment = if (dark) Color(0xFF7F848E) else Color(0xFF8C929D)
    val number = if (dark) Color(0xFFF78C6C) else Color(0xFFB26B00)
    val type = if (dark) Color(0xFFFFCB6B) else Color(0xFF9A6A00)
    val function = if (dark) Color(0xFF82AAFF) else Color(0xFF2F6FEB)

    SyntaxHighlight.tokenize(code).forEach { token ->
        when (token.kind) {
            SyntaxHighlight.Kind.PLAIN -> append(token.text)
            SyntaxHighlight.Kind.COMMENT -> withStyle(
                SpanStyle(color = comment, fontStyle = FontStyle.Italic),
            ) { append(token.text) }
            else -> {
                val color = when (token.kind) {
                    SyntaxHighlight.Kind.KEYWORD -> keyword
                    SyntaxHighlight.Kind.STRING -> string
                    SyntaxHighlight.Kind.NUMBER -> number
                    SyntaxHighlight.Kind.TYPE -> type
                    SyntaxHighlight.Kind.FUNCTION -> function
                    else -> Color.Unspecified
                }
                withStyle(SpanStyle(color = color)) { append(token.text) }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val dark = LocalDarkTheme.current
    val background = if (dark) Color(0xFF1B1F27) else Color(0xFFF7F8FA)
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(0.5.dp, border, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SelectionContainer {
            Text(
                text = highlightedCode(code.trimEnd(), dark),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    val lines = markdown.split('\n')
    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trimEnd()

        // 表格：表头 + 分隔行 + 若干数据行
        if (line.trimStart().startsWith("|") &&
            index + 1 < lines.size &&
            isTableSeparator(lines[index + 1])
        ) {
            flushParagraph()
            val header = splitTableRow(line)
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += Block.Table(header, rows)
            continue
        }

        when {
            line.isBlank() -> flushParagraph()
            isDivider(line) -> {
                flushParagraph()
                blocks += Block.Divider
            }
            line.startsWith("### ") -> {
                flushParagraph()
                blocks += Block.Heading(3, line.removePrefix("### "))
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks += Block.Heading(2, line.removePrefix("## "))
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks += Block.Heading(1, line.removePrefix("# "))
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += Block.Quote(line.removePrefix("> "))
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                blocks += Block.Bullet(line.drop(2))
            }
            Regex("^\\d+\\.\\s").containsMatchIn(line) -> {
                flushParagraph()
                val number = line.substringBefore(' ')
                blocks += Block.Numbered(number, line.substringAfter(' ').trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

private fun isDivider(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.length < 3) return false
    val first = trimmed.first()
    if (first != '-' && first != '*' && first != '_') return false
    return trimmed.all { it == first }
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|") && !trimmed.contains('-')) return false
    return trimmed.contains('-') && trimmed.all { it in "|-: " }
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun inline(
    text: String,
    linkColor: Color,
    linksClickable: Boolean = true,
    codeColor: Color = Color.Unspecified,
): AnnotatedString =
    buildAnnotatedString {
        // 行内公式：$...$ 或 \(...\)，转成 Unicode 近似后仍当作普通文本参与换行
        val pattern = Regex(
            "\\*\\*([^*]+)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\(([^)]+)\\)|\\$([^$\\n]+)\\$|\\\\\\((.+?)\\\\\\)",
        )
        var cursor = 0
        pattern.findAll(text).forEach { m ->
            if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
            val math = m.groupValues[5].ifBlank { m.groupValues[6] }
            if (math.isNotBlank()) {
                withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                    append(Latex.inline(math))
                }
                cursor = m.range.last + 1
                return@forEach
            }
            when {
                m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(m.groupValues[1])
                }
                m.groupValues[2].isNotEmpty() -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        // 官方做法：特殊文字只用灰色区分，不加底色块
                        color = codeColor,
                    ),
                ) { append(m.groupValues[2]) }
                m.groupValues[3].isNotEmpty() -> {
                    val label = m.groupValues[3]
                    val url = m.groupValues[4]
                    val citation = label.all { ch -> ch in "⁰¹²³⁴⁵⁶⁷⁸⁹" }
                    val style = SpanStyle(
                        color = if (citation) Tokens.GrayText else linkColor,
                        fontSize = if (citation) 12.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    )
                    if (linksClickable) {
                        withLink(LinkAnnotation.Url(url)) { withStyle(style) { append(label) } }
                    } else {
                        // 正文保护：链接只是着色文字，点击不会跳转
                        withStyle(style) { append(label) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }

private val SUPERSCRIPT_DIGITS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
)

private fun superscriptOf(value: Int): String =
    value.toString().map { SUPERSCRIPT_DIGITS[it] ?: it }.joinToString("")

/**
 * 角标归一化：
 * 1. 把 `[440](url)` / `[⁴⁴⁰](url)` 这类数字角标按「本轮来源顺序」重排成 1、2、3…；
 * 2. 角标如果跑到了行首（会破坏 ### / - 等块级语法），统一挪到该行末尾；
 * 3. 重复引用同一来源复用同一个序号。
 */
internal fun normalizeCitations(text: String, sources: List<ToolSource>): String {
    val citationLink = Regex("\\[([0-9⁰¹²³⁴⁵⁶⁷⁸⁹]{1,4})]\\(([^)\\s]+)\\)")
    if (!citationLink.containsMatchIn(text)) return text

    val order = sources.mapIndexed { i, s -> s.url to i + 1 }.toMap().toMutableMap()
    var next = sources.size + 1
    val numbered = citationLink.replace(text) { m ->
        val url = m.groupValues[2]
        val number = order.getOrPut(url) { next++ }
        "[${superscriptOf(number)}]($url)"
    }

    // 行首连续角标 → 移到行尾，避免打断标题/列表
    val leading = Regex("^((?:\\[[¹²³⁴⁵⁶⁷⁸⁹]+]\\([^)\\s]+\\)\\s*)+)")
    return numbered.split('\n').joinToString("\n") { line ->
        val match = leading.find(line)
        if (match == null) {
            line
        } else {
            val marks = match.value.trimEnd()
            val rest = line.substring(match.range.last + 1)
            if (rest.isBlank()) line else rest + marks
        }
    }
}

private fun splitSegments(text: String): List<Segment> {
    val result = mutableListOf<Segment>()
    val fence = Regex("```([\\w+-]*)\\n?")
    var index = 0
    while (true) {
        val open = fence.find(text, index) ?: break
        if (open.range.first > index) {
            addMarkdown(result, text.substring(index, open.range.first))
        }
        val close = text.indexOf("```", open.range.last + 1)
        if (close < 0) {
            addMarkdown(result, text.substring(open.range.first))
            return result
        }
        result += Segment.Code(
            language = open.groupValues[1],
            code = text.substring(open.range.last + 1, close),
        )
        index = close + 3
    }
    if (index < text.length) addMarkdown(result, text.substring(index))
    return result
}

/** 在普通 Markdown 片段里切出块级公式（$$...$$ / \[...\]） */
private fun addMarkdown(out: MutableList<Segment>, markdown: String) {
    if (markdown.isEmpty()) return
    val math = Regex("\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\\\]")
    var cursor = 0
    math.findAll(markdown).forEach { m ->
        if (m.range.first > cursor) out += Segment.Markdown(markdown.substring(cursor, m.range.first))
        val latex = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        if (latex.isNotEmpty()) out += Segment.Math(latex)
        cursor = m.range.last + 1
    }
    if (cursor < markdown.length) out += Segment.Markdown(markdown.substring(cursor))
}
