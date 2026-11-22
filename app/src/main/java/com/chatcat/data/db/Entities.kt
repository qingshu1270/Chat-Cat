package com.chatcat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val activeLeafId: String? = null,
    val preview: String = "",
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("parentId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val parentId: String?,
    val role: String,
    /** pending / streaming / done / stopped / failed / refused */
    val status: String,
    val stopReason: String? = null,
    val model: String? = null,
    val createdAt: Long,
    val seq: Long,
    val error: String? = null,
    /** 实际产生过服务端工具块（禁续写） */
    val usedTools: Boolean = false,
    /** 本轮 latest_reminder 快照 */
    val reminder: String? = null,
    /** 多版本：同一提问下只有一条 active 回答参与上下文与展示 */
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    /** 思考耗时（毫秒），用于「已思考（用时 N 秒）」 */
    @ColumnInfo(defaultValue = "0") val thinkingMs: Long = 0,
)

/**
 * 消息附件：本地保留原件 uri 用于预览，[fileId] 是 Files API 返回的 file_id。
 * 导入的历史附件没有本地原件与 fileId，只能展示信息卡。
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val ordinal: Int,
    val name: String,
    val mime: String,
    val size: Long,
    val isImage: Boolean,
    val fileId: String? = null,
    /** content:// 或 file://，仅本机有效 */
    val localUri: String? = null,
)

/**
 * 每次对话请求的 token 用量（流式结束时记录）。
 * 续写会对同一 assistant 消息发多次请求，因此按请求逐行记录，展示时按 messageId 汇总。
 */
@Entity(
    tableName = "usage_records",
    indices = [Index("messageId"), Index("sessionId"), Index("createdAt")],
)
data class UsageRecordEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val sessionId: String,
    val createdAt: Long,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,
    val cacheCreationTokens: Int,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val ordinal: Int,
    /** text / thinking / server_tool_use / web_search_tool_result / web_fetch_tool_result */
    val type: String,
    val text: String = "",
    val payload: String? = null,
    val closed: Boolean = false,
)
