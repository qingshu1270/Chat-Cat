package com.chatcat.domain.context

import com.chatcat.domain.model.Role
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiMessage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 上下文拼装（对齐 chat-demo）：
 * - 只保留「提问 + 可用回答」成对的轮次；
 * - 历史思考与引用不回传，相邻文本拼接；
 * - latest_reminder 仅在内容变化时插入；
 * - 阅读链接工具块在 M1 暂不保留（后续按 signature 回传）。
 */
class ContextAssembler {

    /** 续写前缀：assistant 已生成的内容；思考模式需一并回传 thinking + signature */
    data class Prefill(
        val text: String,
        val thinking: String? = null,
        val signature: String? = null,
    )

    fun buildRequest(
        history: List<UiMessage>,
        question: String,
        model: String,
        thinking: Boolean,
        search: Boolean,
        reminder: String?,
        enableTitle: Boolean,
        prefill: Prefill? = null,
        attachments: List<UiAttachment> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        putJsonObject("thinking") {
            put("type", if (thinking) "enabled" else "disabled")
        }
        putJsonObject("safety_check") { put("level", "high") }
        if (enableTitle) putJsonObject("title") { put("enabled", true) }

        putJsonArray("tools") {
            add(buildJsonObject {
                put("type", "deepseek_read_url")
                put("name", "deepseek_read_url")
            })
            if (search) {
                add(buildJsonObject {
                    put("type", "deepseek_web_search")
                    put("name", "deepseek_web_search")
                })
            }
        }

        put("messages", buildMessages(history, question, reminder, prefill, attachments))
    }

    private fun buildMessages(
        history: List<UiMessage>,
        question: String,
        reminder: String?,
        prefill: Prefill?,
        attachments: List<UiAttachment>,
    ): JsonArray = buildJsonArray {
        var previousReminder: String? = null
        var index = 0
        while (index < history.size) {
            val user = history[index]
            if (user.role != Role.USER) {
                index++
                continue
            }
            val reply = history.getOrNull(index + 1)
            if (reply == null || reply.role != Role.ASSISTANT || !reply.isUsableContext) {
                index++
                continue
            }
            if (reminder != null && reminder != previousReminder) {
                add(buildJsonObject {
                    put("role", "latest_reminder")
                    put("content", reminder)
                })
                previousReminder = reminder
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", userContent(user.text, user.attachments))
            })
            add(buildJsonObject {
                put("role", "assistant")
                put("content", reply.text)
            })
            index += 2
        }

        if (reminder != null && reminder != previousReminder) {
            add(buildJsonObject {
                put("role", "latest_reminder")
                put("content", reminder)
            })
        }
        add(buildJsonObject {
            put("role", "user")
            put("content", userContent(question, attachments))
        })
        if (prefill != null && prefill.text.isNotBlank()) {
            add(buildJsonObject {
                put("role", "assistant")
                if (prefill.thinking.isNullOrBlank()) {
                    put("content", prefill.text)
                } else {
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "thinking")
                            put("thinking", prefill.thinking)
                            put("signature", prefill.signature.orEmpty())
                        })
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prefill.text)
                        })
                    })
                }
            })
        }
    }

    /**
     * 有附件且已拿到 file_id 时用内容块数组：**先文件后文本**（官方顺序）。
     * 没有 file_id 的附件（导入的历史）只能作为文本上下文缺失，不进入请求。
     */
    private fun userContent(text: String, attachments: List<UiAttachment>): JsonElement {
        val files = attachments.filter { !it.fileId.isNullOrBlank() }
        if (files.isEmpty()) return JsonPrimitive(text)
        return buildJsonArray {
            files.forEach { file ->
                add(buildJsonObject {
                    put("type", if (file.isImage) "image" else "document")
                    putJsonObject("source") {
                        put("type", "file")
                        put("file_id", file.fileId)
                    }
                })
            }
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            }
        }
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** 本轮请求是否包含文件块：决定是否携带 `anthropic-beta` 头 */
        fun hasFileBlocks(history: List<UiMessage>, attachments: List<UiAttachment>): Boolean =
            attachments.any { !it.fileId.isNullOrBlank() } ||
                history.any { msg -> msg.attachments.any { !it.fileId.isNullOrBlank() } }

        /**
         * 格式：<date>,<weekday>,<location>,<platform>,<language>
         * platform 只能是 App / Web（官方校验，写 Android 会 400）；location 可为空。
         */
        fun reminder(region: String? = null): String {
            val now = LocalDate.now()
            val weekday = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
            val area = region?.takeIf { it.isNotBlank() } ?: ""
            return "${now.format(dateFormatter)},$weekday,$area,App,中文"
        }
    }
}
