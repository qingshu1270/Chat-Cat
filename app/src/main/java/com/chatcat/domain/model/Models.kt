package com.chatcat.domain.model

enum class Role { USER, ASSISTANT }

enum class MessageStatus { PENDING, STREAMING, DONE, STOPPED, FAILED, REFUSED }

enum class BlockType { TEXT, THINKING, TOOL }

data class Citation(val url: String, val title: String? = null)

/** 已发送的消息附件；[fileId] 为 null 表示本机没有可用原件（如导入的历史） */
data class UiAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val isImage: Boolean,
    val fileId: String? = null,
    val localUri: String? = null,
) {
    val ext: String get() = AttachmentPolicy.extension(name).uppercase()
    val sizeText: String get() = AttachmentPolicy.formatSize(size)
}

data class UiBlock(
    val id: String,
    val ordinal: Int,
    val type: BlockType,
    val text: String,
    val payload: String? = null,
    val closed: Boolean = false,
    /** 文本块内命中的引用，按出现顺序；同一块内按 url 去重 */
    val citations: List<Citation> = emptyList(),
) {
    val isText: Boolean get() = type == BlockType.TEXT
    val isThinking: Boolean get() = type == BlockType.THINKING
}

data class UiMessage(
    val id: String,
    val role: Role,
    val status: MessageStatus,
    val blocks: List<UiBlock> = emptyList(),
    val stopReason: String? = null,
    val model: String? = null,
    val createdAt: Long = 0L,
    val seq: Long = 0L,
    val usedTools: Boolean = false,
    val error: String? = null,
    /** 回答消息所属的提问 id（多版本共用同一提问） */
    val parentId: String? = null,
    val active: Boolean = true,
    /** 当前展示的是第几版（从 1 开始） */
    val versionIndex: Int = 1,
    val versionCount: Int = 1,
    /** 思考耗时，官方「已思考（用时 N 秒）」 */
    val thinkingMs: Long = 0L,
    /** 该条消息携带的附件（用户消息） */
    val attachments: List<UiAttachment> = emptyList(),
) {
    val text: String
        get() = blocks.filter { it.isText }.joinToString("") { it.text }

    val thinking: String
        get() = blocks.filter { it.isThinking }.joinToString("") { it.text }

    val isUsableContext: Boolean
        get() = role == Role.ASSISTANT &&
            (status == MessageStatus.DONE || status == MessageStatus.STOPPED) &&
            stopReason != "refusal" &&
            text.isNotBlank()

    val isStreaming: Boolean
        get() = status == MessageStatus.STREAMING || status == MessageStatus.PENDING

    /** 全部可展示来源，按工具块顺序去重 */
    val sources: List<ToolSource>
        get() = blocks
            .filter { it.type == BlockType.TOOL }
            .flatMap { ToolParser.parse("", it.payload)?.sources.orEmpty() }
            .distinctBy { it.url }

    /** 正文 + 上标引用序号（1 起），序号与 [sources] 顺序一致 */
    val textWithCitations: String
        get() {
            val index = sources.mapIndexed { i, source -> source.url to (i + 1) }.toMap()
            return blocks.filter { it.isText }.joinToString("") { block ->
                val marks = block.citations
                    .mapNotNull { citation -> index[citation.url]?.let { it to citation.url } }
                    .distinctBy { it.first }
                    .sortedBy { it.first }
                    .joinToString("") { (number, url) ->
                        // 上标渲染为 markdown 链接，点按直接用浏览器打开来源
                        "[${superscript(number)}]($url)"
                    }
                block.text + marks
            }
        }

    private fun superscript(value: Int): String =
        value.toString().map { ch ->
            when (ch) {
                '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
                '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
                else -> ch
            }
        }.joinToString("")

    /** 官方：有版本切换器时多一条操作栏 */
    val hasVersions: Boolean get() = versionCount > 1
}

data class SessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val pinned: Boolean,
)

data class UsageInfo(
    val inputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val totalInput: Int get() = inputTokens + cacheReadTokens
}
