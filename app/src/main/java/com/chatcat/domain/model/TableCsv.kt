package com.chatcat.domain.model

/** 表格导出：转义为 CSV（RFC 4180：含逗号/引号/换行的字段用双引号包裹，内部引号 doubled）。 */
object TableCsv {

    fun escape(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = field.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    fun encode(header: List<String>, rows: List<List<String>>): String {
        val lines = mutableListOf<String>()
        if (header.isNotEmpty()) lines += header.joinToString(",") { escape(it) }
        rows.forEach { row -> lines += row.joinToString(",") { escape(it) } }
        return lines.joinToString("\r\n")
    }
}
