package com.chatcat.data.archive

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.chatcat.data.db.AppDatabase
import com.chatcat.data.db.AttachmentEntity
import com.chatcat.data.db.BlockEntity
import com.chatcat.data.db.MessageEntity
import com.chatcat.data.db.SessionEntity
import com.chatcat.domain.model.ArchiveMerge
import com.chatcat.domain.model.BlockType
import com.chatcat.domain.model.MessageStatus
import com.chatcat.domain.model.Role
import com.chatcat.domain.model.UiBlock
import com.chatcat.domain.model.ToolParser
import com.chatcat.domain.model.UiAttachment
import com.chatcat.domain.model.UiMessage
import com.chatcat.domain.model.VersionResolver
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.contentOrNull

/** 自研旧备份的 format 标识：Chat Cat 改名前后两种口径都接受 */
private val LEGACY_FORMATS = setOf("chatcat.archive", "chatdemo.archive")

/** 官方网页端历史导出格式版本（deepseek_data-*.zip） */
const val OFFICIAL_FORMAT_VERSION = 3

/** 预览时无法得知消息数量（包内没有会话索引）的占位值，界面显示为「以实际导入为准」 */
const val UNKNOWN_MESSAGE_COUNT = -1

/** 导入时每批写入的会话条数：只影响写库节奏，实体写入后立即丢弃 */
private const val SESSION_BATCH = 32

/** 单条目超过这个大小就不再按精确长度预分配，走动态扩容，避免被异常 size 拖垮 */
private const val MAX_EXACT_ENTRY_BYTES = 64L * 1024 * 1024

data class ImportPreview(
    val formatLabel: String,
    val sessionCount: Int,
    val messageCount: Int,
    /** user.json 中可识别的显示名；不读取邮箱、手机号等字段 */
    val userName: String?,
    val legacy: Boolean = false,
)

data class ImportResult(val sessions: Int, val messages: Int, val skipped: Int = 0)

/**
 * 导入方式：
 * - [MERGE] 合并增量（推荐）：保留本机已有对话，只补上备份里没有的；同一份备份重复导入不会产生副本
 * - [REPLACE] 覆盖：清空本机全部对话后整体替换，不可撤销
 */
enum class ImportMode { MERGE, REPLACE }

/** 导出用 manifest：只写官方字段，不夹带历史字段 */
@Serializable
private data class V3ManifestOut(val export_format_version: Int = OFFICIAL_FORMAT_VERSION)

@Serializable
private data class V3Manifest(
    val export_format_version: Int = 0,
    val format: String? = null,
    val version: Int = 0,
)

@Serializable
private data class V3SessionFile(
    val id: String = "",
    val title: String? = null,
    val pinned: Boolean = false,
    val current_message_id: Long = 0,
    val inserted_at: String = "",
    val updated_at: String = "",
    val messages: List<V3Message> = emptyList(),
)

@Serializable
private data class V3Message(
    val id: Long = 0,
    val parent_id: Long? = null,
    val role: String = "",
    val status: String = "",
    val thinking_enabled: Boolean = false,
    val search_enabled: Boolean = false,
    val inserted_at: String = "",
    val fragments: List<V3Fragment> = emptyList(),
)

@Serializable
private data class V3Fragment(
    val type: String = "",
    val content: String? = null,
    val elapsed_secs: Double? = null,
    val status: String? = null,
    val url: String? = null,
    val title: String? = null,
    val queries: List<String> = emptyList(),
    val results: List<V3SearchResult> = emptyList(),
    val files: List<V3FileItem> = emptyList(),
)

/** 官方 FILE 片段里的单个文件；本项目不写二进制，`export_path` 恒为 null */
@Serializable
private data class V3FileItem(
    val id: String = "",
    val file_name: String = "",
    val file_size: Long = 0,
    val is_image: Boolean = false,
    val token_usage: Long? = null,
    val inserted_at: String? = null,
    val export_path: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
private data class V3SearchResult(
    val title: String = "",
    val url: String = "",
    val snippet: String? = null,
    val site_name: String? = null,
    val published_at: String? = null,
    val site_icon: String? = null,
)

@Serializable
private data class V3IndexEntry(
    val id: String = "",
    val title: String? = null,
    val pinned: Boolean = false,
    val message_count: Int = 0,
    val inserted_at: String = "",
    val updated_at: String = "",
)

// ---- 兼容 v0.2.6 自研 chatdemo.archive（旧口径，导入时仍识别） ----

@Serializable
private data class LegacyData(
    val format: String = "",
    val version: Int = 0,
    val exportedAt: Long = 0,
    val sessions: List<LegacySession> = emptyList(),
)

@Serializable
private data class LegacySession(
    val id: String = "",
    val title: String = "",
    val model: String = "deepseek-flash",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val pinned: Boolean = false,
    val preview: String = "",
    val messages: List<LegacyMessage> = emptyList(),
)

@Serializable
private data class LegacyMessage(
    val id: String = "",
    val parentId: String? = null,
    val role: String = "assistant",
    val status: String = "done",
    val stopReason: String? = null,
    val model: String? = null,
    val createdAt: Long = 0,
    val seq: Long = 0,
    val error: String? = null,
    val usedTools: Boolean = false,
    val reminder: String? = null,
    val active: Boolean = true,
    val thinkingMs: Long = 0,
    val blocks: List<LegacyBlock> = emptyList(),
)

@Serializable
private data class LegacyBlock(
    val ordinal: Int = 0,
    val type: String = "text",
    val text: String = "",
    val payload: String? = null,
    val closed: Boolean = true,
)

/**
 * 会话归档，读写官方网页端 v3 结构：
 *
 * ```
 * manifest.json                     { "export_format_version": 3 }
 * user.json                         可选，账号信息（导入时询问是否保留用户名）
 * chat_sessions/{session_id}.json   会话消息树
 * chat_session_index.json           会话索引
 * chat_sessions/{id}_files/...      图片（本版本不落盘）
 * ```
 *
 * 同时兼容 v0.2.6 的自研 `chatdemo.archive` 导入。支持两种语义：
 * **合并增量**（默认）保留本机已有会话、只追加缺失的；**覆盖**则整体替换。
 * 两种方式都先完整解析，成功后在单个事务里落库，失败不动原数据。
 */
class ChatArchive(private val db: AppDatabase) {

    private val dao = db.chatDao()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ------------------------------- 导出 -------------------------------

    suspend fun export(resolver: ContentResolver, uri: Uri): Int = withContext(Dispatchers.IO) {
        val sessions = dao.allSessions()
        val index = mutableListOf<V3IndexEntry>()
        var exportedSessions = 0

        val output = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入所选文件")

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(V3ManifestOut()).toByteArray())
            zip.closeEntry()

            // 一个会话一个条目：读一条、写一条、丢一条，
            // 不再把所有会话先编码成 ByteArray 攒在 List 里（大历史同样会把堆吃满）
            sessions.forEach { session ->
                val all = dao.messages(session.id).map {
                    it.toUi(dao.blocks(it.id), dao.attachments(it.id))
                }
                if (all.isEmpty()) return@forEach

                val (currentId, exported) = buildExportMessages(all)
                if (exported.isEmpty()) return@forEach

                val file = V3SessionFile(
                    id = session.id,
                    title = session.title.ifBlank { null },
                    pinned = session.pinned,
                    current_message_id = currentId,
                    inserted_at = iso(session.createdAt),
                    updated_at = iso(session.updatedAt),
                    messages = exported,
                )
                zip.putNextEntry(ZipEntry("chat_sessions/${session.id}.json"))
                zip.write(json.encodeToString(file).toByteArray())
                zip.closeEntry()

                index += V3IndexEntry(
                    id = session.id,
                    title = session.title.ifBlank { null },
                    pinned = session.pinned,
                    message_count = exported.size,
                    inserted_at = iso(session.createdAt),
                    updated_at = iso(session.updatedAt),
                )
                exportedSessions++
            }

            zip.putNextEntry(ZipEntry("chat_session_index.json"))
            zip.write(json.encodeToString(index).toByteArray())
            zip.closeEntry()
        }
        exportedSessions
    }

    /**
     * 本地会话 → 官方消息树：
     * - 只导出当前可见分支（「修改」隐藏的旧分支不导出）；
     * - 同一提问的多个回答版本作为兄弟节点，current_message_id 指向当前版本；
     * - 工具块无结构化数据，跳过，但保留 search_enabled 标记。
     */
    private fun buildExportMessages(all: List<UiMessage>): Pair<Long, List<V3Message>> {
        val visible = VersionResolver.resolve(all)
        val groups = all
            .filter { it.role == Role.ASSISTANT && it.parentId != null }
            .groupBy { it.parentId.orEmpty() }

        var nextId = 1L
        var previousAssistantId: Long? = null
        var currentMessageId = 0L
        val out = mutableListOf<V3Message>()

        visible.forEachIndexed { index, message ->
            if (message.role != Role.USER) return@forEachIndexed

            val userId = nextId++
            out += V3Message(
                id = userId,
                parent_id = previousAssistantId,
                role = "USER",
                status = "FINISHED",
                inserted_at = iso(message.createdAt),
                fragments = buildList {
                    // 官方顺序：文件在前，文本在后
                    if (message.attachments.isNotEmpty()) {
                        add(
                            V3Fragment(
                                type = "FILE",
                                files = message.attachments.map { file ->
                                    V3FileItem(
                                        id = file.id,
                                        file_name = file.name,
                                        file_size = file.size,
                                        is_image = file.isImage,
                                        inserted_at = iso(message.createdAt),
                                    )
                                },
                            ),
                        )
                    }
                    if (message.text.isNotBlank()) {
                        add(V3Fragment(type = "REQUEST", content = message.text))
                    }
                },
            )

            val versions = groups[message.id].orEmpty().sortedBy { it.seq }
            if (versions.isNotEmpty()) {
                val active = versions.firstOrNull { it.active } ?: versions.last()
                versions.forEach { version ->
                    val assistantId = nextId++
                    out += version.toV3(assistantId, parentId = userId)
                    if (version.id == active.id) {
                        previousAssistantId = assistantId
                        currentMessageId = assistantId
                    }
                }
            } else {
                // 旧数据：回答没有 parentId，紧跟在提问后面
                val legacyReply = visible.getOrNull(index + 1)
                    ?.takeIf { it.role == Role.ASSISTANT }
                if (legacyReply != null) {
                    val assistantId = nextId++
                    out += legacyReply.toV3(assistantId, parentId = userId)
                    previousAssistantId = assistantId
                    currentMessageId = assistantId
                } else {
                    previousAssistantId = userId
                    currentMessageId = userId
                }
            }
        }
        return currentMessageId to out
    }

    private fun UiMessage.toV3(id: Long, parentId: Long?): V3Message {
        val fragments = mutableListOf<V3Fragment>()
        blocks.forEach { block ->
            when {
                block.isThinking && block.text.isNotBlank() -> fragments += V3Fragment(
                    type = "THINK",
                    content = block.text,
                    elapsed_secs = if (thinkingMs > 0) thinkingMs / 1000.0 else null,
                )

                block.isText && block.text.isNotBlank() ->
                    fragments += V3Fragment(type = "RESPONSE", content = block.text)

                block.type == BlockType.TOOL -> {
                    val info = ToolParser.parse("", block.payload) ?: return@forEach
                    if (info.isSearch) {
                        fragments += V3Fragment(
                            type = "TOOL_SEARCH",
                            status = "FINISHED",
                            queries = info.queries,
                            results = info.sources.map { source ->
                                V3SearchResult(
                                    title = source.title,
                                    url = source.url,
                                    snippet = source.snippet,
                                    site_name = source.siteName,
                                )
                            },
                        )
                    } else if (info.isFetch) {
                        fragments += V3Fragment(
                            type = "TOOL_OPEN",
                            status = "FINISHED",
                            url = info.url,
                            title = info.title,
                        )
                    }
                }
            }
        }

        val v3Status = when {
            status == MessageStatus.REFUSED -> "CONTENT_FILTER"
            status == MessageStatus.FAILED -> "FAILED"
            status == MessageStatus.STOPPED -> "INCOMPLETE"
            status == MessageStatus.PENDING || status == MessageStatus.STREAMING -> "WIP"
            stopReason == "max_tokens" -> "INCOMPLETE"
            else -> "FINISHED"
        }
        return V3Message(
            id = id,
            parent_id = parentId,
            role = "ASSISTANT",
            status = v3Status,
            thinking_enabled = blocks.any { it.isThinking },
            search_enabled = usedTools,
            inserted_at = iso(createdAt),
            fragments = fragments,
        )
    }

    // ------------------------------- 预览 -------------------------------

    suspend fun inspect(resolver: ContentResolver, uri: Uri): ImportPreview {
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取所选文件")
        return input.use { inspect(it) }
    }

    /** 已下载到本机缓存的导出包 */
    suspend fun inspect(file: File): ImportPreview = FileInputStream(file).use { inspect(it) }

    /**
     * 预览只读元信息（manifest / user / 会话索引），**不解析会话正文**。
     *
     * 旧实现把所有会话文件读进一个 Map 再整体反序列化，140MB 的包会直接把堆撑爆
     * （OOM 被系统杀掉，表现为闪退）。现在这一步的内存占用与整包大小无关。
     */
    suspend fun inspect(input: InputStream): ImportPreview = withContext(Dispatchers.IO) {
        var manifestBytes: ByteArray? = null
        var userBytes: ByteArray? = null
        var indexBytes: ByteArray? = null
        var legacyBytes: ByteArray? = null
        var sessionEntries = 0

        ZipWalker(input).use { walker ->
            while (true) {
                val name = walker.nextEntry() ?: break
                when {
                    name == "manifest.json" -> manifestBytes = walker.readCurrent()
                    name == "user.json" -> userBytes = walker.readCurrent()
                    name == "chat_session_index.json" -> indexBytes = walker.readCurrent()
                    // 只有自研旧格式才有 data.json，体量很小；官方 v3 包里不会出现
                    name == "data.json" -> legacyBytes = walker.readCurrent()
                    SESSION_PATH.matches(name) -> sessionEntries++
                }
            }
        }

        val manifestText = manifestBytes?.decodeToString()
            ?: throw IllegalArgumentException("压缩包内缺少 manifest.json")

        if (manifestText.contains("\"format\"")) {
            val data = legacyBytes?.decodeToString()
                ?: throw IllegalArgumentException("压缩包内缺少 data.json")
            val legacy = json.decodeFromString<LegacyData>(data)
            require(legacy.format in LEGACY_FORMATS) { "不是可识别的备份文件" }
            return@withContext ImportPreview(
                formatLabel = "Chat Cat v${legacy.version}",
                sessionCount = legacy.sessions.size,
                messageCount = legacy.sessions.sumOf { it.messages.size },
                userName = null,
                legacy = true,
            )
        }

        val manifest = json.decodeFromString<V3Manifest>(manifestText)
        require(manifest.export_format_version == OFFICIAL_FORMAT_VERSION) {
            "不支持的备份版本：${manifest.export_format_version}"
        }

        // 索引里带着每条会话的消息数，预览无需解析正文
        val index = indexBytes?.let { parseIndex(it) }
        val count = index?.size?.takeIf { it > 0 } ?: sessionEntries
        require(count > 0) { "压缩包内没有可导入的会话" }

        ImportPreview(
            formatLabel = "deepseek v$OFFICIAL_FORMAT_VERSION",
            sessionCount = count,
            // 没有索引时无法在不解析正文的前提下统计消息数，交给界面显示「以实际导入为准」
            messageCount = index?.sumOf { it.message_count } ?: UNKNOWN_MESSAGE_COUNT,
            userName = userBytes?.let { extractUserName(it.decodeToString()) },
        )
    }

    // ------------------------------- 导入 -------------------------------

    suspend fun import(
        resolver: ContentResolver,
        uri: Uri,
        mode: ImportMode = ImportMode.MERGE,
        onProgress: ((Int) -> Unit)? = null,
    ): ImportResult {
        // 先只读包首的 manifest：格式或版本不对就根本不碰本机数据
        val legacy = peekIsLegacy {
            resolver.openInputStream(uri) ?: throw IllegalArgumentException("无法读取所选文件")
        }
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取所选文件")
        return input.use {
            if (legacy) importLegacyStream(it, mode) else importV3(it, mode, onProgress)
        }
    }

    /** 已下载到本机缓存的导出包 */
    suspend fun import(
        file: File,
        mode: ImportMode = ImportMode.MERGE,
        onProgress: ((Int) -> Unit)? = null,
    ): ImportResult {
        val legacy = peekIsLegacy { FileInputStream(file) }
        return FileInputStream(file).use {
            if (legacy) importLegacyStream(it, mode) else importV3(it, mode, onProgress)
        }
    }

    /**
     * 单流入口：先落到临时文件再按常规流程导入。
     * 不这么做就没法「先看 manifest 再决定怎么读」，而把整包读进内存正是要修的问题。
     */
    suspend fun import(input: InputStream, mode: ImportMode = ImportMode.MERGE): ImportResult =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("chatcat_import_", ".zip")
            try {
                input.use { src ->
                    temp.outputStream().use { out -> src.copyTo(out, 64 * 1024) }
                }
                import(temp, mode)
            } finally {
                temp.delete()
            }
        }

    /**
     * 自研旧格式：data.json 体量很小，仍然整体解析（与原逻辑一致）。
     */
    private suspend fun importLegacyStream(input: InputStream, mode: ImportMode): ImportResult {
        var data: ByteArray? = null
        ZipWalker(input).use { walker ->
            while (true) {
                val name = walker.nextEntry() ?: break
                if (name == "data.json") data = walker.readCurrent()
            }
        }
        val raw = data?.decodeToString() ?: throw IllegalArgumentException("压缩包内缺少 data.json")
        return importLegacy(raw, mode)
    }

    /**
     * 官方 v3 包：**逐条会话流式导入**。
     *
     * - 任何时刻内存里只有「一条会话的字节 + 它解析出来的对象」，入库后立刻丢弃；
     * - 每 [SESSION_BATCH] 条提交一次；
     * - 合并模式下整包是幂等的（按标题 + 创建时间去重），因此逐批提交是安全的：
     *   中途失败或被系统杀掉，直接再导入一次就会补全，不会产生副本；
     * - 覆盖模式是「先清空再写」，所以整包放在一个事务里，要么全成要么全不动。
     */
    private suspend fun importV3(
        input: InputStream,
        mode: ImportMode,
        onProgress: ((Int) -> Unit)? = null,
    ): ImportResult {
        val seen = if (mode == ImportMode.MERGE) {
            dao.allSessions().mapTo(HashSet()) { ArchiveMerge.fingerprint(it.title, it.createdAt) }
        } else {
            HashSet()
        }

        var sessions = 0
        var messages = 0
        var skipped = 0
        var manifestOk = false
        val parsedIds = HashSet<String>()
        val indexIds = HashSet<String>()
        val batch = ArrayList<Pair<SessionEntity, List<LocalMessage>>>(SESSION_BATCH)

        suspend fun flush() {
            if (batch.isEmpty()) return
            val pending = batch.toList()
            batch.clear()
            db.withTransaction {
                pending.forEach { (session, list) -> writeSession(session, list) }
            }
            onProgress?.invoke(sessions)
        }

        suspend fun walk() {
            ZipWalker(input).use { walker ->
                while (true) {
                    val name = walker.nextEntry() ?: break
                    when {
                        name == "manifest.json" -> {
                            val manifest = json.decodeFromString<V3Manifest>(
                                walker.readCurrent().decodeToString(),
                            )
                            require(manifest.export_format_version == OFFICIAL_FORMAT_VERSION) {
                                "备份版本不受支持"
                            }
                            manifestOk = true
                        }

                        name == "chat_session_index.json" -> {
                            parseIndex(walker.readCurrent())?.forEach { indexIds += it.id }
                        }

                        SESSION_PATH.matches(name) -> {
                            // manifest 必须先出现：否则无法确认格式，也不能安全地清库
                            require(manifestOk) { "压缩包结构异常：manifest.json 不在包首，请重新导出" }

                            // 单条会话异常（超大 / 损坏 / 单条就把堆吃光）只跳过这一条，不拖垮整包
                            val local = try {
                                val bytes = walker.readCurrent()
                                val file = json.decodeFromString<V3SessionFile>(bytes.decodeToString())
                                parsedIds += file.id
                                val key = ArchiveMerge.fingerprint(
                                    file.title.orEmpty(),
                                    parseIso(file.inserted_at),
                                )
                                if (mode == ImportMode.MERGE && !seen.add(key)) {
                                    null
                                } else {
                                    toLocalSession(file)
                                }
                            } catch (e: OutOfMemoryError) {
                                Log.w("ChatCat", "skip oversized session $name", e)
                                null
                            } catch (e: Exception) {
                                Log.w("ChatCat", "skip broken session $name", e)
                                null
                            }

                            if (local == null) {
                                skipped++
                                continue
                            }
                            batch += local
                            sessions++
                            messages += local.second.size
                            if (batch.size >= SESSION_BATCH) flush()
                        }
                    }
                }
            }
        }

        if (mode == ImportMode.REPLACE) {
            db.withTransaction {
                dao.deleteAllSessions()
                walk()
            }
        } else {
            walk()
        }
        flush()

        // 索引一致性：与旧实现同一口径（条数相同且 id 全部对上）
        if (indexIds.isNotEmpty()) {
            require(indexIds.size == parsedIds.size && parsedIds.containsAll(indexIds)) {
                "会话索引与文件不一致"
            }
        }
        require(sessions > 0 || skipped > 0) { "压缩包内没有可导入的会话" }
        return ImportResult(sessions, messages, skipped)
    }

    private suspend fun writeSession(session: SessionEntity, messages: List<LocalMessage>) {
        dao.upsertSession(session)
        messages.forEach { local ->
            dao.upsertMessage(local.entity)
            if (local.blocks.isNotEmpty()) dao.upsertBlocks(local.blocks)
            if (local.attachments.isNotEmpty()) dao.upsertAttachments(local.attachments)
        }
    }

    private suspend fun importLegacy(raw: String, mode: ImportMode): ImportResult {
        val legacy = json.decodeFromString<LegacyData>(raw)
        require(legacy.format in LEGACY_FORMATS) { "不是可识别的备份文件" }

        val prepared = legacy.sessions.map { session ->
            val sessionId = UUID.randomUUID().toString()
            val idMap = mutableMapOf<String, String>()
            val messages = session.messages.sortedBy { it.seq }.map { m ->
                val newId = UUID.randomUUID().toString()
                idMap[m.id] = newId
                MessageEntity(
                    id = newId,
                    sessionId = sessionId,
                    parentId = m.parentId?.let { idMap[it] },
                    role = m.role,
                    status = m.status,
                    stopReason = m.stopReason,
                    model = m.model,
                    createdAt = m.createdAt,
                    seq = m.seq,
                    error = m.error,
                    usedTools = m.usedTools,
                    reminder = m.reminder,
                    active = m.active,
                    thinkingMs = m.thinkingMs,
                ) to m.blocks.map { b ->
                    BlockEntity(
                        id = UUID.randomUUID().toString(),
                        messageId = newId,
                        ordinal = b.ordinal,
                        type = b.type,
                        text = b.text,
                        payload = b.payload,
                        closed = b.closed,
                    )
                }
            }
            SessionEntity(
                id = sessionId,
                title = session.title,
                model = session.model,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                pinned = session.pinned,
                activeLeafId = null,
                preview = session.preview,
            ) to messages
        }

        val incoming = if (mode == ImportMode.MERGE) {
            pickNew(
                prepared.map { it.first.title to it.first.createdAt },
                dao.allSessions().map { it.title to it.createdAt },
            ).map { prepared[it] }
        } else {
            prepared
        }
        val skipped = prepared.size - incoming.size

        db.withTransaction {
            if (mode == ImportMode.REPLACE) dao.deleteAllSessions()
            incoming.forEach { (session, messages) ->
                dao.upsertSession(session)
                messages.forEach { (entity, blocks) ->
                    dao.upsertMessage(entity)
                    if (blocks.isNotEmpty()) dao.upsertBlocks(blocks)
                }
            }
        }
        return ImportResult(incoming.size, incoming.sumOf { it.second.size }, skipped)
    }

    /** 合并去重：按「标题 + 创建时间」挑出本机还没有的会话，口径见 [ArchiveMerge] */
    private fun pickNew(
        incoming: List<Pair<String, Long>>,
        existing: List<Pair<String, Long>>,
    ): List<Int> = ArchiveMerge.newIndices(incoming, existing)

    /**
     * 官方消息树 → 本机线性消息：沿 current_message_id 回溯到根，只导入当前分支。
     */
    private fun toLocalSession(file: V3SessionFile): Pair<SessionEntity, List<LocalMessage>> {
        val byId = file.messages.associateBy { it.id }
        val current = byId[file.current_message_id] ?: file.messages.maxByOrNull { it.id }

        val branch = mutableListOf<V3Message>()
        var cursor = current
        val guard = mutableSetOf<Long>()
        while (cursor != null && guard.add(cursor.id)) {
            branch += cursor
            cursor = cursor.parent_id?.let { byId[it] }
        }
        branch.reverse()

        val sessionId = UUID.randomUUID().toString()
        val out = mutableListOf<LocalMessage>()
        var seq = 0L
        var lastUser: String? = null

        branch.forEach { message ->
            val isUser = message.role == "USER"
            if (!isUser && message.role != "ASSISTANT") return@forEach
            val id = UUID.randomUUID().toString()
            seq += 1

            val blocks = mutableListOf<BlockEntity>()
            val attachments = mutableListOf<AttachmentEntity>()
            var usedTools = message.search_enabled

            if (isUser) {
                val requestText = message.fragments
                    .filter { it.type == "REQUEST" }
                    .joinToString("") { it.content.orEmpty() }
                val files = message.fragments
                    .filter { it.type == "FILE" }
                    .flatMap { it.files }
                files.forEachIndexed { index, file ->
                    attachments += AttachmentEntity(
                        id = UUID.randomUUID().toString(),
                        messageId = id,
                        ordinal = index,
                        name = file.file_name.ifBlank { "附件 ${index + 1}" },
                        mime = if (file.is_image) "image/*" else "",
                        size = file.file_size,
                        isImage = file.is_image,
                        // 官方包里的 id 属于导出账号，换机后不可用；本地原件也未随包携带
                        fileId = null,
                        localUri = null,
                    )
                }
                if (requestText.isNotBlank()) {
                    blocks += BlockEntity(
                        id = UUID.randomUUID().toString(),
                        messageId = id,
                        ordinal = 0,
                        type = "text",
                        text = requestText,
                        closed = true,
                    )
                }
            } else {
                var ordinal = 0
                message.fragments.forEach { fragment ->
                    when (fragment.type) {
                        "THINK" -> blocks += BlockEntity(
                            id = UUID.randomUUID().toString(),
                            messageId = id,
                            ordinal = ordinal++,
                            type = "thinking",
                            text = fragment.content.orEmpty(),
                            closed = true,
                        )
                        "RESPONSE", "TEMPLATE_RESPONSE" -> blocks += BlockEntity(
                            id = UUID.randomUUID().toString(),
                            messageId = id,
                            ordinal = ordinal++,
                            type = "text",
                            text = fragment.content.orEmpty(),
                            closed = true,
                        )
                        "TOOL_SEARCH", "TOOL_OPEN", "TOOL_FIND", "READ_LINK", "TOOL_IMAGE_SEARCH" -> {
                            usedTools = true
                            blocks += BlockEntity(
                                id = UUID.randomUUID().toString(),
                                messageId = id,
                                ordinal = ordinal++,
                                type = "tool",
                                payload = fragment.toToolPayload(),
                                closed = true,
                            )
                        }
                        else -> Unit
                    }
                }
            }

            val refused = !isUser && message.status == "CONTENT_FILTER"
            val status = when {
                isUser -> "done"
                refused -> "refused"
                message.status == "FAILED" ||
                    message.status == "TIMEOUT" ||
                    message.status == "CONTEXT_LENGTH_EXCEEDED" -> "failed"
                message.status == "INCOMPLETE" || message.status == "WIP" -> "stopped"
                else -> "done"
            }
            val thinkingMs = message.fragments
                .filter { it.type == "THINK" }
                .mapNotNull { it.elapsed_secs }
                .maxOrNull()
                ?.times(1000)
                ?.toLong()
                ?: 0L

            val kept = (if (refused) blocks.filter { it.type == "tool" } else blocks)
                .mapIndexed { index, block -> block.copy(ordinal = index) }

            out += LocalMessage(
                entity = MessageEntity(
                    id = id,
                    sessionId = sessionId,
                    parentId = if (isUser) null else lastUser,
                    role = if (isUser) "user" else "assistant",
                    status = status,
                    stopReason = when {
                        refused -> "refusal"
                        status == "stopped" -> "max_tokens"
                        else -> "end_turn"
                    },
                    model = null,
                    createdAt = parseIso(message.inserted_at),
                    seq = seq,
                    error = if (status == "failed") "导入的历史回答生成失败" else null,
                    usedTools = usedTools,
                    active = true,
                    thinkingMs = thinkingMs,
                ),
                blocks = kept,
                attachments = attachments,
            )
            if (isUser) lastUser = id
        }

        val preview = out.asSequence()
            .filter { it.entity.role == "user" }
            .map { msg ->
                msg.blocks.firstOrNull()?.text?.takeIf { it.isNotBlank() }
                    ?: msg.attachments.firstOrNull()?.name.orEmpty()
            }
            .take(1)
            .joinToString("")
            .replace('\n', ' ')
            .take(60)

        val session = SessionEntity(
            id = sessionId,
            title = file.title.orEmpty(),
            model = "deepseek-flash",
            createdAt = parseIso(file.inserted_at),
            updatedAt = parseIso(file.updated_at),
            pinned = file.pinned,
            activeLeafId = null,
            preview = preview,
        )
        return session to out
    }

    // ------------------------------- 工具 -------------------------------

    private fun parseIndex(bytes: ByteArray): List<V3IndexEntry>? =
        runCatching { json.decodeFromString<List<V3IndexEntry>>(bytes.decodeToString()) }.getOrNull()

    /**
     * 只读包首的 manifest 判断是不是自研旧格式；顺手把 v3 版本号校验掉。
     * manifest 在包首时只需解压极少数据，代价可以忽略。
     */
    private suspend fun peekIsLegacy(open: () -> InputStream): Boolean = withContext(Dispatchers.IO) {
        val manifestText = open().use { input ->
            var text: String? = null
            ZipWalker(input).use { walker ->
                while (true) {
                    val name = walker.nextEntry() ?: break
                    if (name == "manifest.json") {
                        text = walker.readCurrent().decodeToString()
                        break
                    }
                }
            }
            text
        } ?: throw IllegalArgumentException("压缩包内缺少 manifest.json")

        if (manifestText.contains("\"format\"")) return@withContext true
        val manifest = json.decodeFromString<V3Manifest>(manifestText)
        require(manifest.export_format_version == OFFICIAL_FORMAT_VERSION) { "备份版本不受支持" }
        false
    }

    /** user.json 只取显示名；邮箱、手机号等一律不读。 */
    private fun extractUserName(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return null
        val keys = listOf("name", "username", "nickname", "display_name", "user_name")

        fun find(obj: JsonObject, depth: Int): String? {
            keys.forEach { key ->
                val value = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()
                if (!value.isNullOrBlank()) return value
            }
            if (depth >= 3) return null

            // oauth_profiles: [{ provider, profile_json: "{\"name\":\"...\"}" }]
            (obj["oauth_profiles"] as? JsonArray)?.forEach { element ->
                val profile = element as? JsonObject ?: return@forEach
                val inner = (profile["profile_json"] as? JsonPrimitive)?.contentOrNull
                if (!inner.isNullOrBlank()) {
                    val parsed = runCatching {
                        json.parseToJsonElement(inner) as? JsonObject
                    }.getOrNull()
                    if (parsed != null) find(parsed, depth + 1)?.let { return it }
                }
                find(profile, depth + 1)?.let { return it }
            }

            listOf("user", "profile", "account").forEach { key ->
                val child = obj[key] as? JsonObject
                if (child != null) find(child, depth + 1)?.let { return it }
            }
            return null
        }
        return find(root, 0)
    }

    /** 归档工具片段 → 与流式工具块一致的 JSON，供思维链展示与再次导出 */
    private fun V3Fragment.toToolPayload(): String = buildJsonObject {
        when (type) {
            "TOOL_SEARCH" -> {
                put("type", "web_search_tool_result")
                put("status", status ?: "FINISHED")
                putJsonArray("queries") { queries.forEach { add(it) } }
                putJsonArray("content") {
                    results.forEach { result ->
                        add(buildJsonObject {
                            put("type", "web_search_result")
                            put("title", result.title)
                            put("url", result.url)
                            put("snippet", result.snippet)
                            put("site_name", result.site_name)
                            put("page_age", result.published_at)
                        })
                    }
                }
            }
            else -> {
                put("type", "web_fetch_tool_result")
                put("status", status ?: "FINISHED")
                put("url", url)
                put("title", title)
                put("content", buildJsonObject {
                    put("type", "web_fetch_result")
                    put("url", url)
                    put("title", title)
                })
            }
        }
    }.toString()

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
        attachments = attachments.map { file ->
            UiAttachment(
                id = file.id,
                name = file.name,
                mime = file.mime,
                size = file.size,
                isImage = file.isImage,
                fileId = file.fileId,
                localUri = file.localUri,
            )
        },
    )

    private fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun parseIso(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private data class LocalMessage(
        val entity: MessageEntity,
        val blocks: List<BlockEntity>,
        val attachments: List<AttachmentEntity> = emptyList(),
    )

    companion object {
        internal val SESSION_PATH = Regex("^chat_sessions/([^/]+)\\.json$")
    }
}

/** 归档里我们认识的顶层文件名（不含外层包裹目录时） */
private fun isArchiveFile(name: String): Boolean =
    name == "manifest.json" ||
        name == "user.json" ||
        name == "data.json" ||
        name == "chat_session_index.json" ||
        ChatArchive.SESSION_PATH.matches(name)

/** 首个文件条目的顶层目录（deepseek_data-xxx/）即视为整包前缀；无目录返回空串 */
internal fun detectPrefix(rawName: String): String {
    if (!rawName.contains('/')) return ""
    return rawName.substringBefore('/') + "/"
}

internal fun stripPrefix(rawName: String, prefix: String): String =
    if (prefix.isNotEmpty() && rawName.startsWith(prefix)) rawName.removePrefix(prefix) else rawName

/**
 * 逐条遍历 ZIP 的读指针。
 *
 * 与「一次读完所有条目」不同：只有调用 [readCurrent] 时才会把当前条目读成字节，
 * 读完即丢，所以常驻内存只和「单条会话」有关，与整包大小无关。
 */
internal class ZipWalker(input: InputStream) : Closeable {
    private val zip = ZipInputStream(BufferedInputStream(input, 64 * 1024))
    private var prefix: String? = null
    private var currentSize: Long = -1
    private var done = false

    /** 前进到下一条文件条目并返回其（已剥离外层目录的）路径；读完返回 null */
    fun nextEntry(): String? {
        if (done) return null
        while (true) {
            val entry = zip.nextEntry ?: run { done = true; return null }
            if (entry.isDirectory) {
                // 目录条目（如 chat_sessions/）不参与前缀判定，否则会被误当成外层包裹目录
                zip.closeEntry()
                continue
            }
            val known = prefix
            if (known == null) {
                val candidate = detectPrefix(entry.name)
                // 候选前缀只有在「剥掉之后确实是一条归档文件」时才成立；
                // 否则第一个文件正好是 chat_sessions/x.json 时会被错剥成 x.json
                prefix = if (candidate.isNotEmpty() && isArchiveFile(stripPrefix(entry.name, candidate))) {
                    candidate
                } else {
                    ""
                }
            }
            currentSize = entry.size
            return stripPrefix(entry.name, prefix.orEmpty())
        }
    }

    /** 读取当前条目的全部字节（每个条目只能读一次） */
    fun readCurrent(): ByteArray {
        val size = currentSize
        // 本地头里带解压后大小时按精确长度分配，避免 readBytes 反复扩容造成的瞬时双份
        if (size in 1..MAX_EXACT_ENTRY_BYTES) {
            val buffer = ByteArray(size.toInt())
            var read = 0
            while (read < buffer.size) {
                val n = zip.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            return if (read == buffer.size) buffer else buffer.copyOf(read)
        }
        return zip.readBytes()
    }

    override fun close() {
        runCatching { zip.close() }
    }
}
