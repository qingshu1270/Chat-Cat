package com.chatcat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY createdAt ASC")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM messages ORDER BY sessionId ASC, seq ASC")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT * FROM blocks ORDER BY messageId ASC, ordinal ASC")
    suspend fun allBlocks(): List<BlockEntity>

    @Query("SELECT * FROM attachments ORDER BY messageId ASC, ordinal ASC")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query(
        """
        SELECT a.* FROM attachments a
        INNER JOIN messages m ON a.messageId = m.id
        WHERE m.sessionId = :sessionId
        ORDER BY m.seq ASC, a.ordinal ASC
        """,
    )
    fun observeAttachments(sessionId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY ordinal ASC")
    suspend fun attachments(messageId: String): List<AttachmentEntity>

    @Upsert
    suspend fun upsertAttachments(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachments(messageId: String)

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: String): Flow<SessionEntity?>

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    /** 删除没有任何消息的空会话（重复点「新建」留下的草稿），保留当前会话 */
    @Query("DELETE FROM sessions WHERE id != :keepId AND id NOT IN (SELECT DISTINCT sessionId FROM messages)")
    suspend fun deleteEmptySessions(keepId: String)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY seq ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY seq ASC")
    suspend fun messages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageEntity?

    /** 「修改」产生的提问版本链：根 + 所有 parentId 指向根的提问 */
    @Query("SELECT * FROM messages WHERE role = 'user' AND (id = :rootId OR parentId = :rootId) ORDER BY seq ASC")
    suspend fun userVersions(rootId: String): List<MessageEntity>

    @Query("UPDATE messages SET active = 0 WHERE role = 'user' AND (id = :rootId OR parentId = :rootId)")
    suspend fun deactivateUserVersions(rootId: String)

    @Query("SELECT COALESCE(MAX(seq), 0) FROM messages WHERE sessionId = :sessionId")
    suspend fun maxSeq(sessionId: String): Long

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status, stopReason = :stopReason, error = :error WHERE id = :id")
    suspend fun settleMessage(id: String, status: String, stopReason: String?, error: String?)

    @Query("UPDATE messages SET thinkingMs = :ms WHERE id = :id")
    suspend fun setThinkingMs(id: String, ms: Long)

    /** 同一提问下的其他回答全部置为非活动（多版本） */
    @Query("UPDATE messages SET active = 0 WHERE parentId = :parentId")
    suspend fun deactivateVersions(parentId: String)

    @Query("UPDATE messages SET active = 1 WHERE id = :id")
    suspend fun activateMessage(id: String)

    /** 旧数据没有 parentId：首次重新生成时把它挂到对应提问下，才能参与版本切换 */
    @Query("UPDATE messages SET parentId = :parentId WHERE id = :id AND parentId IS NULL")
    suspend fun attachParent(id: String, parentId: String)

    @Query("SELECT * FROM messages WHERE parentId = :parentId ORDER BY seq ASC")
    suspend fun versions(parentId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND seq > :seq")
    suspend fun deleteMessagesAfter(sessionId: String, seq: Long)

    /** 修改提问：该条及其之后的消息整体置为旧分支（保留数据，不再展示） */
    @Query("UPDATE messages SET active = 0 WHERE sessionId = :sessionId AND seq >= :seq")
    suspend fun deactivateFrom(sessionId: String, seq: Long)

    @Query(
        """
        SELECT b.* FROM blocks b
        INNER JOIN messages m ON b.messageId = m.id
        WHERE m.sessionId = :sessionId
        ORDER BY m.seq ASC, b.ordinal ASC
        """,
    )
    fun observeBlocks(sessionId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE messageId = :messageId ORDER BY ordinal ASC")
    suspend fun blocks(messageId: String): List<BlockEntity>

    @Upsert
    suspend fun upsertBlocks(blocks: List<BlockEntity>)

    @Upsert
    suspend fun upsertBlock(block: BlockEntity)

    @Query("DELETE FROM blocks WHERE messageId = :messageId")
    suspend fun deleteBlocks(messageId: String)

    @Transaction
    suspend fun replaceBlocks(messageId: String, blocks: List<BlockEntity>) {
        deleteBlocks(messageId)
        upsertBlocks(blocks)
    }

    /** 记录一次请求的 token 用量 */
    @Query(
        """
        INSERT INTO usage_records
        (id, messageId, sessionId, createdAt, model, inputTokens, outputTokens, cacheReadTokens, cacheCreationTokens)
        VALUES (:id, :messageId, :sessionId, :createdAt, :model, :inputTokens, :outputTokens, :cacheReadTokens, :cacheCreationTokens)
        """,
    )
    suspend fun insertUsage(
        id: String, messageId: String, sessionId: String, createdAt: Long, model: String,
        inputTokens: Int, outputTokens: Int, cacheReadTokens: Int, cacheCreationTokens: Int,
    )

    @Query("SELECT * FROM usage_records WHERE messageId = :messageId")
    suspend fun usageForMessage(messageId: String): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records WHERE createdAt >= :since")
    suspend fun usageSince(since: Long): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records")
    suspend fun allUsage(): List<UsageRecordEntity>

    /** 冷启动时把仍处于 streaming 的消息标记为中断，避免永远转圈 */
    @Query("UPDATE messages SET status = 'stopped' WHERE status IN ('streaming', 'pending')")
    suspend fun settleDanglingMessages()
}
