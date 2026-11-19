package com.chatcat.domain.model

/**
 * 轻量语法高亮分词（离线、无外部依赖）。只做「像官方」的视觉区分，不做完整 AST 解析。
 * 返回 (片段, 类别) 列表；类别用于 UI 选色。
 */
object SyntaxHighlight {

    /**
     * 词法类别：除关键字/字符串/注释/数字外，额外区分类型名与函数调用，
     * 这样配色层次更接近常见编辑器主题（官方代码块也能看出这种层次）。
     */
    enum class Kind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION }

    data class Token(val text: String, val kind: Kind)

    private val KEYWORDS = setOf(
        // Kotlin / Java / JS / TS / Python / Go / Rust / C / C++ / SQL 常见关键字
        "fun", "val", "var", "class", "object", "interface", "data", "return", "if", "else",
        "for", "while", "when", "in", "is", "as", "import", "package", "override", "suspend",
        "private", "public", "protected", "internal", "companion", "null", "true", "false",
        "this", "super", "new", "void", "int", "long", "double", "float", "boolean", "String",
        "const", "let", "const", "function", "def", "lambda", "async", "await", "export",
        "default", "from", "try", "catch", "finally", "throw", "throws", "switch", "case",
        "break", "continue", "static", "final", "struct", "enum", "impl", "fn", "pub", "use",
        "match", "Some", "None", "Ok", "Err", "select", "where", "insert", "update", "delete",
    )

    /** 单行注释前缀（按语言） */
    private val LINE_COMMENT = listOf("//", "#", "--")
    private val BLOCK_COMMENT_START = "/*"
    private val BLOCK_COMMENT_END = "*/"

    /**
     * 逐字符扫描：字符串（' " `）优先，其次注释，再数字与标识符。
     * 块注释可跨行，因此整段 code 一起处理。
     */
    fun tokenize(code: String): List<Token> {
        val out = mutableListOf<Token>()
        val buf = StringBuilder()
        var kind = Kind.PLAIN
        var i = 0
        var inBlockComment = false
        var quote: Char? = null

        /** 当前标识符后面（跳过空格）紧跟 '(' 就按函数名上色 */
        fun isCallAhead(): Boolean {
            var j = i
            while (j < code.length && code[j] == ' ') j++
            return j < code.length && code[j] == '('
        }

        fun flush() {
            if (buf.isNotEmpty()) {
                // 标识符再细分：关键字 / 类型名 / 函数调用
                val word = buf.toString()
                val finalKind = when {
                    kind != Kind.PLAIN -> kind
                    KEYWORDS.contains(word) -> Kind.KEYWORD
                    word.firstOrNull()?.isUpperCase() == true -> Kind.TYPE
                    isCallAhead() -> Kind.FUNCTION
                    else -> Kind.PLAIN
                }
                out += Token(buf.toString(), finalKind)
                buf.clear()
            }
        }

        while (i < code.length) {
            val c = code[i]
            val next = if (i + 1 < code.length) code[i + 1] else '\u0000'

            if (inBlockComment) {
                if (kind != Kind.COMMENT) { flush(); kind = Kind.COMMENT }
                buf.append(c)
                if (c == '*' && next == '/') {
                    buf.append(next); i++; inBlockComment = false; flush(); kind = Kind.PLAIN
                }
                i++
                continue
            }

            if (quote != null) {
                if (kind != Kind.STRING) { flush(); kind = Kind.STRING }
                buf.append(c)
                if (c == '\\' && i + 1 < code.length) {
                    buf.append(next); i++
                } else if (c == quote) {
                    flush(); kind = Kind.PLAIN; quote = null
                }
                i++
                continue
            }

            when {
                c == '"' || c == '\'' || c == '`' -> {
                    flush(); kind = Kind.STRING; quote = c; buf.append(c); i++
                }
                c == '/' && next == '*' -> {
                    flush(); kind = Kind.COMMENT; inBlockComment = true
                    buf.append(c).append(next); i += 2
                }
                LINE_COMMENT.any { code.startsWith(it, i) } -> {
                    flush(); kind = Kind.COMMENT
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    buf.append(code, i, end)
                    out += Token(buf.toString(), Kind.COMMENT)
                    buf.clear(); kind = Kind.PLAIN; i = end
                }
                c.isDigit() -> {
                    if (kind != Kind.NUMBER) { flush(); kind = Kind.NUMBER }
                    buf.append(c); i++
                }
                c.isLetterOrDigit() || c == '_' -> {
                    if (kind != Kind.PLAIN) { flush(); kind = Kind.PLAIN }
                    buf.append(c); i++
                }
                else -> {
                    flush(); buf.append(c); flush(); i++
                }
            }
        }
        flush()
        return out
    }
}
