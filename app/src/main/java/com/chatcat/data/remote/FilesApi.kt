package com.chatcat.data.remote

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * Files API（Anthropic 兼容）：`POST {base}/anthropic/v1/files`。
 * 上传成功后返回 file_id，消息里用 `image` / `document` 块引用。
 */
class FilesApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 上传单个文件。[purpose] 为 null 时不传该字段（图片），
     * 其余文件按 `rich_layout_recognition` / `user_data` 分流。
     */
    suspend fun upload(
        baseUrl: String,
        apiKey: String,
        resolver: ContentResolver,
        uri: Uri,
        name: String,
        mime: String,
        purpose: String?,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/anthropic/v1/files"
        val fileBody = ContentRequestBody(resolver, uri, mime.toMediaTypeOrNull(), onProgress)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        // purpose 必须排在 file 之前
        if (purpose != null) builder.addFormDataPart("purpose", purpose)
        builder.addFormDataPart("file", name, fileBody)

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-beta", FILES_BETA)
            .post(builder.build())
            .build()

        val call = client.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancelHandle = job?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                if (job?.isCancelled == true) throw CancellationException()
                throw ApiException(ApiException.Kind.NETWORK, e.message ?: "网络连接失败")
            }
            response.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException.fromHttp(r.code, text)
                val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                val id = (root?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                id?.takeIf { it.isNotBlank() }
                    ?: throw ApiException(ApiException.Kind.PROTOCOL, "上传响应缺少 file_id")
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    companion object {
        /** 通过 file_id 引用文件时必须携带 */
        const val FILES_BETA = "files-api-2025-04-14"
    }
}

/** 直接流式写入 socket，顺带回传进度；避免把大文件读进内存 */
private class ContentRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: okhttp3.MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    /** SAF 能给出长度时按固定长度发送，避免服务端不认 chunked 的情况 */
    private val length: Long = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)

    override fun contentType() = mediaType

    override fun contentLength(): Long = if (length > 0) length else -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                if (length > 0) onProgress((written.toFloat() / length).coerceIn(0f, 1f))
            }
        }
    }
}
