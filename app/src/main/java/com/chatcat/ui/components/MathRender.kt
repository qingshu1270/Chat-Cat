package com.chatcat.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * 轻量 LaTeX 渲染。
 *
 * 只覆盖大模型回答里常见的写法，不追求完整 TeX：
 * 上下标、`\frac`、`\sqrt`、希腊字母与常用运算符、`\text{}`、`\left(...\right)`。
 * 块级公式排版成真正的分式/根号；行内公式转成 Unicode 近似（x²、α、≤），
 * 这样行内文本仍然是单段文本，不会被拆成碎片、破坏自动换行。
 */
object Latex {

    // ---------------- 符号表 ----------------

    private val SYMBOLS = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ",
        "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "iota" to "ι",
        "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ",
        "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "cdot" to "·",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
        "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "sum" to "∑", "prod" to "∏",
        "int" to "∫", "iint" to "∬", "oint" to "∮", "sqrt" to "√",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
        "Leftarrow" to "⇐", "leftrightarrow" to "↔", "mapsto" to "↦",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
        "supset" to "⊃", "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "forall" to "∀", "exists" to "∃", "therefore" to "∴", "because" to "∵",
        "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "degree" to "°",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "prime" to "′",
        "quad" to " ", "qquad" to "  ", ";" to " ", "," to " ", "!" to "",
        "{" to "{", "}" to "}", "\\" to "\n", "%" to "%", "&" to "",
    )

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ',
    )

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'n' to 'ₙ',
        'x' to 'ₓ', 'k' to 'ₖ', 'm' to 'ₘ', 'p' to 'ₚ', 's' to 'ₛ', 't' to 'ₜ',
    )

    // ---------------- 行内：转 Unicode ----------------

    /** 行内公式 → 近似 Unicode，保证仍然是一段可换行的文本 */
    fun inline(src: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> {
                    val cmd = readCommand(src, i)
                    val name = cmd.first
                    i = cmd.second
                    when (name) {
                        "frac" -> {
                            val a = readGroup(src, i)
                            val b = readGroup(src, a.second)
                            out.append('(').append(inline(a.first)).append(")/(")
                                .append(inline(b.first)).append(')')
                            i = b.second
                        }
                        "sqrt" -> {
                            val group = readGroup(src, i)
                            out.append('√').append('(').append(inline(group.first)).append(')')
                            i = group.second
                        }
                        "text", "mathrm", "mathbf", "operatorname" -> {
                            val group = readGroup(src, i)
                            out.append(inline(group.first))
                            i = group.second
                        }
                        "left", "right" -> Unit
                        else -> out.append(SYMBOLS[name] ?: name)
                    }
                }
                c == '^' || c == '_' -> {
                    val table = if (c == '^') SUPERSCRIPTS else SUBSCRIPTS
                    val group = readGroup(src, i + 1)
                    out.append(group.first.map { table[it] ?: it }.joinToString(""))
                    i = group.second
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    // ---------------- 块级：解析成节点树 ----------------

    sealed interface Node {
        data class Symbol(val text: String, val bold: Boolean = false) : Node
        data class Row(val children: List<Node>) : Node
        data class Frac(val num: List<Node>, val den: List<Node>) : Node
        data class Sqrt(val body: List<Node>, val index: List<Node>?) : Node
        data class Script(val base: List<Node>, val sup: List<Node>?, val sub: List<Node>?) : Node
    }

    fun parse(src: String): List<Node> {
        val parser = Parser(src)
        return parser.parseRow(null)
    }

    private class Parser(val src: String) {
        var pos = 0

        fun parseRow(stop: Char?): List<Node> {
            val nodes = mutableListOf<Node>()
            val buffer = StringBuilder()

            fun flush() {
                if (buffer.isNotEmpty()) {
                    nodes += Node.Symbol(buffer.toString())
                    buffer.clear()
                }
            }

            while (pos < src.length) {
                val c = src[pos]
                if (stop != null && c == stop) {
                    pos++
                    flush()
                    return nodes
                }
                when {
                    c == '\\' -> {
                        flush()
                        val start = pos
                        pos++
                        while (pos < src.length && src[pos].isLetter()) pos++
                        val name = src.substring(start + 1, pos)
                        when (name) {
                            "frac", "dfrac", "tfrac" -> {
                                val a = groupOrAtom()
                                val b = groupOrAtom()
                                nodes += Node.Frac(a, b)
                            }
                            "sqrt" -> {
                                var index: List<Node>? = null
                                if (pos < src.length && src[pos] == '[') {
                                    pos++
                                    index = parseRow(']')
                                }
                                nodes += Node.Sqrt(groupOrAtom(), index)
                            }
                            "text", "mathrm", "mathbf", "operatorname" -> {
                                val body = groupOrAtom()
                                val joined = body.joinToString("") { plain(it) }
                                nodes += Node.Symbol(joined, bold = name == "mathbf")
                            }
                            "left", "right" -> {
                                // 只取分隔符本身，尺寸忽略
                                if (pos < src.length) {
                                    val d = src[pos]
                                    if (d == '\\') {
                                        pos++
                                        while (pos < src.length && src[pos].isLetter()) pos++
                                        nodes += Node.Symbol("|")
                                    } else {
                                        pos++
                                        if (d != '.') nodes += Node.Symbol(d.toString())
                                    }
                                }
                            }
                            "overline", "bar" -> {
                                val body = groupOrAtom()
                                nodes += Node.Row(body)
                            }
                            "quad", "qquad", " ", "," -> nodes += Node.Symbol(" ")
                            "\\" -> nodes += Node.Symbol(" ")
                            else -> nodes += Node.Symbol(SYMBOLS[name] ?: name)
                        }
                    }
                    c == '{' -> {
                        flush()
                        pos++
                        nodes += Node.Row(parseRow('}'))
                    }
                    c == '^' || c == '_' -> {
                        flush()
                        pos++
                        val body = groupOrAtom()
                        val last = nodes.removeLastOrNull()
                        val base = last ?: Node.Symbol("")
                        val existing = base as? Node.Script
                        nodes += if (c == '^') {
                            Node.Script(existing?.base ?: listOf(base), body, existing?.sub)
                        } else {
                            Node.Script(existing?.base ?: listOf(base), existing?.sup, body)
                        }
                    }
                    else -> {
                        buffer.append(if (c.isWhitespace()) ' ' else c)
                        pos++
                    }
                }
            }
            flush()
            return nodes
        }

        /** 读取 `{...}` 或单个原子（`x^2` 里的 2） */
        fun groupOrAtom(): List<Node> {
            if (pos >= src.length) return emptyList()
            if (src[pos] == '{') {
                pos++
                return parseRow('}')
            }
            val c = src[pos]
            if (c == '\\') {
                val start = pos
                pos++
                while (pos < src.length && src[pos].isLetter()) pos++
                val name = src.substring(start + 1, pos)
                return listOf(Node.Symbol(SYMBOLS[name] ?: name))
            }
            pos++
            return listOf(Node.Symbol(c.toString()))
        }
    }

    private fun plain(node: Node): String = when (node) {
        is Node.Symbol -> node.text
        is Node.Row -> node.children.joinToString("") { plain(it) }
        is Node.Frac -> plain(Node.Row(node.num)) + "/" + plain(Node.Row(node.den))
        is Node.Sqrt -> "√(" + node.body.joinToString("") { plain(it) } + ")"
        is Node.Script -> node.base.joinToString("") { plain(it) } +
            (node.sup?.joinToString("") { plain(it) } ?: "") +
            (node.sub?.joinToString("") { plain(it) } ?: "")
    }
}

/**
 * 块级公式：居中排版，过宽时横向滚动。
 * [color] / [fontSize] 由调用方给（思考链里传灰色与更小字号，保持与正文统一）。
 */
@Composable
fun MathBlock(
    latex: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val nodes = remember(latex) { Latex.parse(latex) }
    val contentColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val size = if (fontSize.isSpecified) fontSize else 17.sp
    Box(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        MathRow(nodes, contentColor, size)
    }
}

@Composable
private fun MathRow(nodes: List<Latex.Node>, color: Color, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        nodes.forEach { node -> MathNodeView(node, color, size) }
    }
}

@Composable
private fun MathNodeView(node: Latex.Node, color: Color, size: TextUnit) {
    when (node) {
        is Latex.Node.Symbol -> Text(
            text = node.text,
            color = color,
            fontSize = size,
            lineHeight = size * 1.25f,
            fontWeight = if (node.bold) FontWeight.SemiBold else FontWeight.Normal,
        )

        is Latex.Node.Row -> MathRow(node.children, color, size)

        is Latex.Node.Frac -> {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MathRow(node.num, color, size * 0.78f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .drawBehind { drawLine(color, Offset(0f, 0f), Offset(this.size.width, 0f), 1f) },
                )
                MathRow(node.den, color, size * 0.78f)
            }
        }

        is Latex.Node.Sqrt -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.index != null) {
                    Box(Modifier.offset(y = (-6).dp)) {
                        MathRow(node.index, color, size * 0.6f)
                    }
                }
                Text("√", color = color, fontSize = size * 0.95f)
                Box(
                    Modifier
                        .drawBehind {
                            val stroke = 1.dp.toPx()
                            drawLine(color, Offset(0f, stroke / 2), Offset(this.size.width, stroke / 2), stroke)
                        }
                        .padding(top = 2.dp, start = 2.dp, end = 3.dp),
                ) {
                    MathRow(node.body, color, size)
                }
            }
        }

        is Latex.Node.Script -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathRow(node.base, color, size)
                Column(
                    modifier = Modifier.offset(y = (-3).dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    node.sup?.let { MathRow(it, color, size * 0.58f) }
                    node.sub?.let {
                        Box(Modifier.offset(y = (if (node.sup == null) 6.dp else 1.dp))) {
                            MathRow(it, color, size * 0.58f)
                        }
                    }
                }
            }
        }
    }
}

/** 读取 `\命令名`，返回命令名与下一个位置 */
private fun readCommand(src: String, start: Int): Pair<String, Int> {
    var i = start + 1
    while (i < src.length && src[i].isLetter()) i++
    return src.substring(start + 1, i) to i
}

/** 读取 `{...}` 或单个原子，返回内容与下一个位置 */
private fun readGroup(src: String, start: Int): Pair<String, Int> {
    if (start >= src.length) return "" to start
    if (src[start] == '{') {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start + 1, i) to i + 1
                }
            }
            i++
        }
        return src.substring(start + 1) to src.length
    }
    if (src[start] == '\\') {
        val cmd = readCommand(src, start)
        return cmd.first to cmd.second
    }
    return src[start].toString() to start + 1
}
