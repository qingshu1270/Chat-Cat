package com.chatcat.domain.model

/**
 * 附件策略（对齐 chat-demo 与 Files API 文档）：
 * - 扩展名白名单，不在清单内直接拒绝；
 * - 图片不传 purpose；PDF / Word / PPT 走 `rich_layout_recognition`；其余按 `user_data`；
 * - 单文件上限：解析类 64 MB，user_data 32 MB；
 * - 单次请求最多 20 个文档（图片不计），会话内最多 50 个附件；
 * - `deepseek-v4-pro` 不支持图片，发送前拦截。
 */
object AttachmentPolicy {

    const val MAX_FILES = 50
    const val MAX_DOCS_PER_REQUEST = 20
    const val MAX_SIZE_MB = 64
    const val MAX_USER_DATA_SIZE_MB = 32

    /** 与服务端支持的文件类型保持一致，未列出的扩展名一律拒绝 */
    val ACCEPTED_EXTS = setOf(
        // 图片
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff", "ico",
        // 文档
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "txt", "md",
        "rtf", "epub", "mobi", "json", "xml", "yaml", "yml", "toml", "html", "htm",
        // 代码
        "js", "jsx", "ts", "tsx", "py", "java", "kt", "kts", "go", "rs", "c", "cc",
        "cpp", "h", "hpp", "cs", "rb", "php", "swift", "sql", "sh", "bash", "css",
        "scss", "vue", "svelte", "wxml", "wxss", "wxs", "axml", "acss", "sjs",
    )

    private val LAYOUT_EXTS = setOf("pdf", "doc", "docx", "ppt", "pptx")

    /** 不支持图片的模型 */
    private const val MODEL_PRO = "deepseek-v4-pro"

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase() else ""
    }

    fun isAccepted(name: String): Boolean = extension(name) in ACCEPTED_EXTS

    fun isImage(mime: String, name: String): Boolean {
        if (mime.startsWith("image/")) return true
        if (mime.isNotBlank()) return false
        return extension(name) in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff", "ico")
    }

    /** 图片返回 null（不传 purpose）；其余按是否需要版面解析分流 */
    fun purpose(name: String, isImage: Boolean): String? {
        if (isImage) return null
        return if (extension(name) in LAYOUT_EXTS) "rich_layout_recognition" else "user_data"
    }

    fun maxSizeMb(name: String, isImage: Boolean): Int =
        if (purpose(name, isImage) == "user_data") MAX_USER_DATA_SIZE_MB else MAX_SIZE_MB

    fun supportsImages(model: String): Boolean = model != MODEL_PRO

    /**
     * 添加附件前的整批校验；返回中文原因，通过返回 null。
     * 与官方一致：整批校验，任一不满足则一个都不添加。
     */
    fun validate(files: List<Pair<String, Long>>, currentCount: Int): String? {
        if (files.isEmpty()) return null
        if (files.any { !isAccepted(it.first) }) return "包含不支持的文件类型"
        val oversized = files.firstOrNull { (name, size) ->
            size > maxSizeMb(name, isImage("", name)) * 1024L * 1024L
        }
        if (oversized != null) {
            val limit = maxSizeMb(oversized.first, isImage("", oversized.first))
            return "单个文件不能超过 ${limit}MB"
        }
        if (currentCount + files.size > MAX_FILES) return "最多上传 $MAX_FILES 个文件"
        return null
    }

    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB")

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble() / 1024
        var unit = 1
        while (value >= 1024 && unit < SIZE_UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        val text = if (value < 10) {
            String.format(java.util.Locale.US, "%.1f", value)
        } else {
            value.toInt().toString()
        }
        return "$text ${SIZE_UNITS[unit]}"
    }
}
