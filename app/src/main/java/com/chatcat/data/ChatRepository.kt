package com.chatcat.data

import androidx.room.withTransaction
import com.chatcat.data.db.AppDatabase
import com.chatcat.data.db.AttachmentEntity
import com.chatcat.data.db.BlockEntity
import com.chatcat.data.db.MessageEntity
import com.chatcat.data.db.SessionEntity
import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.CitationCodec
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.SessionSummary
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.UiMessage
import com.chatcat.domain.model.UsageSummary
import com.chatcat.domain.model.VersionResolver
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ChatRepository(private val db: AppDatabase) {

    private val dao = db.chatDao()

    fun sessions(): Flow<List<SessionSummary>> = dao.observeSessions().map { list ->
        list.map {
            SessionSummary(
                id = it.id,
                title = it.title,
                preview = it.preview,
                updatedAt = it.updatedAt,
                pinned = it.pinned,
            )
        }
    }

    fun conversation(sessionId: String): Flow<List<UiMessage>> =
        combine(
            dao.observeMessages(sessionId),
            dao.observeBlocks(sessionId),
            dao.observeAttachments(sessionId),
        ) { messages, blocks, attachments ->
            val byMessage = blocks.groupBy { it.messageId }
            val filesByMessage = attachments.groupBy { it.messageId }
            VersionResolver.resolve(
                messages.map { m ->
                    m.toUi(byMessage[m.id].orEmpty(), filesByMessage[m.id].orEmpty())
                },
            )
        }

    suspend fun session(id: String): SessionEntity? = dao.session(id)

    suspend fun createSession(model: String): SessionEntity {
        val now = System.currentTimeMillis()
        val entity = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            model = model,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertSession(entity)
        return entity
    }

    suspend fun updateSessionModel(sessionId: String, model: String) {
        val s = dao.session(sessionId) ?: return
        dao.upsertSession(s.copy(model = model, updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameSession(sessionId: String, title: String) {
        val s = dao.session(sessionId) ?: return
        dao.upsertSession(s.copy(title = title))
    }

    suspend fun pinSession(sessionId: String, pinned: Boolean) {
        val s = dao.session(sessionId) ?: return
        dao.upsertSession(s.copy(pinned = pinned))
    }

    suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)

    suspend fun deleteAllSessions() = dao.deleteAllSessions()

    /** 只删除没有消息的草稿会话，避免重复点「新建」堆出一串空对话 */
    suspend fun deleteEmptySessions(keepId: String) = dao.deleteEmptySessions(keepId)

    suspend fun deleteMessage(messageId: String) = dao.deleteMessage(messageId)

    /** 修改提问：从该消息起（含）整体转入旧分支，旧问答保留在库里，只是不再展示 */
    suspend fun deactivateFrom(sessionId: String, seq: Long) = dao.deactivateFrom(sessionId, seq)

    suspend fun appendUserMessage(
        sessionId: String,
        text: String,
        attachments: List<UiAttachment> = emptyList(),
        parentId: String? = null,
    ): String {
        val seq = dao.maxSeq(sessionId) + 1
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            parentId = parentId,
            role = "user",
            status = "done",
            createdAt = System.currentTimeMillis(),
            seq = seq,
        )
        dao.upsertMessage(entity)
        dao.upsertBlock(
            BlockEntity(
                id = UUID.randomUUID().toString(),
                messageId = entity.id,
                ordinal = 0,
                type = "text",
                text = text,
                closed = true,
            ),
        )
        if (attachments.isNotEmpty()) {
            dao.upsertAttachments(
                attachments.mapIndexed { index, file ->
                    AttachmentEntity(
                        id = file.id,
                        messageId = entity.id,
                        ordinal = index,
                        name = file.name,
                        mime = file.mime,
                        size = file.size,
                        isImage = file.isImage,
                        fileId = file.fileId,
                        localUri = file.localUri,
                    )
                },
            )
        }
        touchSession(sessionId, text.ifBlank { attachments.firstOrNull()?.name.orEmpty() })
        return entity.id
    }

    /**
     * 新建回答占位。带 [parentUserId] 时视为「同一提问的新版本」：
     * 先把同组其他回答置为非活动，再插入本条。
     */
    suspend fun appendAssistantPlaceholder(
        sessionId: String,
        model: String,
        parentUserId: String?,
    ): MessageEntity = db.withTransaction {
        if (parentUserId != null) dao.deactivateVersions(parentUserId)
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            parentId = parentUserId,
            role = "assistant",
            status = "streaming",
            model = model,
            createdAt = System.currentTimeMillis(),
            seq = dao.maxSeq(sessionId) + 1,
        )
        dao.upsertMessage(entity)
        entity
    }

    suspend fun finishAssistant(
        messageId: String,
        status: MessageStatus,
        stopReason: String?,
        blocks: List<UiBlock>,
        usedTools: Boolean,
        thinkingMs: Long = 0,
    ) {
        val entities = blocks.mapIndexed { i, b ->
            BlockEntity(
                id = b.id,
                messageId = messageId,
                ordinal = i,
                type = when (b.type) {
                    BlockType.TEXT -> "text"
                    BlockType.THINKING -> "thinking"
                    BlockType.TOOL -> "tool"
                },
                text = b.text,
                payload = if (b.type == BlockType.TEXT) {
                    CitationCodec.encode(b.citations) ?: b.payload
                } else {
                    b.payload
                },
                closed = true,
            )
        }
        db.withTransaction {
            dao.deleteBlocks(messageId)
            if (entities.isNotEmpty()) dao.upsertBlocks(entities)
            dao.settleMessage(
                id = messageId,
                status = status.name.lowercase(),
                stopReason = stopReason,
                error = null,
            )
            if (thinkingMs > 0) dao.setThinkingMs(messageId, thinkingMs)
        }
    }

    suspend fun failAssistant(messageId: String, status: MessageStatus, error: String) {
        dao.settleMessage(messageId, status.name.lowercase(), null, error)
    }

    suspend fun versions(parentUserId: String): List<MessageEntity> = dao.versions(parentUserId)

    suspend fun attachToParent(messageId: String, parentUserId: String) =
        dao.attachParent(messageId, parentUserId)

    suspend fun activateVersion(parentUserId: String, messageId: String) {
        db.withTransaction {
            dao.deactivateVersions(parentUserId)
            dao.activateMessage(messageId)
        }
    }

    suspend fun touchSession(sessionId: String, preview: String) {
        val s = dao.session(sessionId) ?: return
        dao.upsertSession(
            s.copy(
                updatedAt = System.currentTimeMillis(),
                preview = preview.replace('\n', ' ').take(60),
            ),
        )
    }

    suspend fun settleDangling() = dao.settleDanglingMessages()

    // ---------- 用量 ----------

    suspend fun recordUsage(
        messageId: String,
        sessionId: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cacheReadTokens: Int,
        cacheCreationTokens: Int,
    ) {
        dao.insertUsage(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheCreationTokens = cacheCreationTokens,
        )
    }

    suspend fun usageOfMessage(messageId: String): UsageSummary =
        UsageSummary.of(dao.usageForMessage(messageId).map {
            UsageSummary(
                inputTokens = it.inputTokens.toLong(),
                outputTokens = it.outputTokens.toLong(),
                cacheReadTokens = it.cacheReadTokens.toLong(),
                cacheCreationTokens = it.cacheCreationTokens.toLong(),
                model = it.model,
            )
        })

    suspend fun usageSince(since: Long): UsageSummary =
        UsageSummary.of(dao.usageSince(since).map {
            UsageSummary(
                inputTokens = it.inputTokens.toLong(),
                outputTokens = it.outputTokens.toLong(),
                cacheReadTokens = it.cacheReadTokens.toLong(),
                cacheCreationTokens = it.cacheCreationTokens.toLong(),
                model = it.model,
            )
        })

    suspend fun allUsage() = dao.allUsage()

    suspend fun allAttachments() = dao.allAttachments()

    /** file_id 失效补传后回写，保证后续轮次使用新的 file_id */
    suspend fun updateAttachmentFileIds(messageId: String, ids: List<Pair<String, String?>>) {
        val current = dao.attachments(messageId)
        if (current.isEmpty()) return
        val map = ids.toMap()
        dao.upsertAttachments(
            current.map { file ->
                if (map.containsKey(file.id)) file.copy(fileId = map[file.id]) else file
            },
        )
    }

    /**
     * 切换「修改」产生的提问版本：先回到版本链的根，再把目标版本设为活动。
     * 各版本各自的回答保留自己的 active 标记，切回时会一起恢复。
     */
    suspend fun switchUserVersion(messageId: String, delta: Int) {
        var current = dao.message(messageId) ?: return
        val guard = mutableSetOf<String>()
        while (true) {
            val parentId = current.parentId ?: break
            if (!guard.add(current.id)) break
            val parent = dao.message(parentId) ?: break
            if (parent.role != "user") break
            current = parent
        }
        val versions = dao.userVersions(current.id)
        val index = versions.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val target = versions.getOrNull(index + delta) ?: return
        db.withTransaction {
            dao.deactivateUserVersions(current.id)
            dao.activateMessage(target.id)
        }
    }

    private fun MessageEntity.toUi(
        blocks: List<BlockEntity>,
        attachments: List<AttachmentEntity> = emptyList(),
    ): UiMessage = UiMessage(
        id = id,
        role = if (role == "user") Role.USER else Role.ASSISTANT,
        status = runCatching { MessageStatus.valueOf(status.uppercase()) }
            .getOrDefault(MessageStatus.DONE),
        blocks = blocks.map {
            UiBlock(
                id = it.id,
                ordinal = it.ordinal,
                type = when (it.type) {
                    "thinking" -> BlockType.THINKING
                    "tool" -> BlockType.TOOL
                    else -> BlockType.TEXT
                },
                text = it.text,
                payload = it.payload,
                closed = it.closed,
                citations = CitationCodec.decode(it.type, it.payload),
            )
        },
        stopReason = stopReason,
        model = model,
        createdAt = createdAt,
        seq = seq,
        usedTools = usedTools,
        error = error,
        parentId = parentId,
        active = active,
        thinkingMs = thinkingMs,
        attachments = attachments.map {
            UiAttachment(
                id = it.id,
                name = it.name,
                mime = it.mime,
                size = it.size,
                isImage = it.isImage,
                fileId = it.fileId,
                localUri = it.localUri,
            )
        },
    )
}
