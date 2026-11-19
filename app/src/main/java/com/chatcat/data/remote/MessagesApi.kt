package com.chatcat.data.remote

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic 兼容 Messages 协议（DeepSeek）。
 * 只负责 HTTP + SSE 解析，不做持久化与 UI。
 */
class MessagesApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(
        baseUrl: String,
        apiKey: String,
        body: JsonObject,
        hasFiles: Boolean = false,
    ): Flow<StreamEvent> = flow {
        val url = baseUrl.trimEnd('/') + "/anthropic/v1/messages"
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .apply {
                // 通过 file_id 引用图片/文档时必须携带
                if (hasFiles) header("anthropic-beta", FilesApi.FILES_BETA)
            }
            .post(body.toString().toRequestBody(JSON))
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

            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw ApiException.fromHttp(response.code, text)
            }

            val source = response.body?.source()
                ?: throw ApiException(ApiException.Kind.PROTOCOL, "响应体为空")

            var stopReason: String? = null
            var usage: Usage? = null
            var sawStop = false

            for (frame in SseReader(source).frames()) {
                if (frame.data.isBlank()) continue
                val root = runCatching { json.parseToJsonElement(frame.data) as? JsonObject }
                    .getOrNull() ?: throw ApiException(ApiException.Kind.PROTOCOL, "无法解析的流数据")

                (root["error"] as? JsonObject)?.let { err ->
                    throw ApiException(
                        ApiException.Kind.STREAM_INTERRUPTED,
                        err["message"].str() ?: "流式返回错误",
                    )
                }

                when (root["type"].str()) {
                    "message_start" -> {
                        val message = root["message"] as? JsonObject ?: continue
                        emit(
                            StreamEvent.Started(
                                id = message["id"].str().orEmpty(),
                                model = message["model"].str().orEmpty(),
                                usage = (message["usage"] as? JsonObject)?.let(::parseUsage),
                            ),
                        )
                    }

                    "content_block_start" -> {
                        val index = root["index"].int() ?: continue
                        val cb = root["content_block"] as? JsonObject ?: continue
                        val block = when (cb["type"].str()) {
                            "text" -> StartBlock.Text(cb["text"].str().orEmpty())
                            "thinking" -> StartBlock.Thinking(
                                thinking = cb["thinking"].str().orEmpty(),
                                signature = cb["signature"].str().orEmpty(),
                            )
                            "server_tool_use", "web_search_tool_result", "web_fetch_tool_result" ->
                                StartBlock.Tool(
                                    type = cb["type"].str().orEmpty(),
                                    name = cb["name"].str(),
                                    kind = cb["kind"].str(),
                                    payload = cb.toString(),
                                )
                            else -> null
                        }
                        if (block != null) emit(StreamEvent.BlockStarted(index, block))
                    }

                    "content_block_delta" -> {
                        val index = root["index"].int() ?: continue
                        val delta = root["delta"] as? JsonObject ?: continue
                        when (delta["type"].str()) {
                            "text_delta" -> emit(
                                StreamEvent.TextDelta(index, delta["text"].str().orEmpty()),
                            )
                            "thinking_delta" -> emit(
                                StreamEvent.ThinkingDelta(index, delta["thinking"].str().orEmpty()),
                            )
                            "signature_delta" -> emit(
                                StreamEvent.SignatureDelta(index, delta["signature"].str().orEmpty()),
                            )
                            "citations_delta" -> {
                                val citation = delta["citation"] as? JsonObject ?: continue
                                emit(
                                    StreamEvent.CitationDelta(
                                        index = index,
                                        url = citation["url"].str().orEmpty(),
                                        title = citation["title"].str(),
                                        citedText = citation["cited_text"].str().orEmpty(),
                                    ),
                                )
                            }
                        }
                    }

                    "content_block_stop" -> {
                        val index = root["index"].int() ?: continue
                        emit(StreamEvent.BlockStopped(index))
                    }

                    "message_delta" -> {
                        val delta = root["delta"] as? JsonObject
                        stopReason = delta?.get("stop_reason").str()
                        delta?.get("title").str()?.takeIf { it.isNotBlank() }
                            ?.let { emit(StreamEvent.TitleReceived(it)) }
                        (root["usage"] as? JsonObject)?.let { usage = parseUsage(it) }
                    }

                    "message_stop" -> {
                        sawStop = true
                        emit(StreamEvent.Finished(stopReason, usage))
                    }

                    else -> Unit // ping 等忽略
                }

                if (sawStop) break
            }

            if (!sawStop) {
                throw ApiException(
                    ApiException.Kind.STREAM_INTERRUPTED,
                    "流在 message_stop 之前结束",
                )
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseUsage(obj: JsonObject): Usage = Usage(
        inputTokens = obj["input_tokens"].int() ?: 0,
        outputTokens = obj["output_tokens"].int() ?: 0,
        cacheReadTokens = obj["cache_read_input_tokens"].int() ?: 0,
        cacheCreationTokens = obj["cache_creation_input_tokens"].int() ?: 0,
    )

    /** 用 GET /models 校验 Key，不产生费用。 */
    suspend fun verifyKey(baseUrl: String, apiKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ApiException.fromHttp(response.code, response.body?.string().orEmpty())
                    }
                }
            }
        }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

        private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    }
}
