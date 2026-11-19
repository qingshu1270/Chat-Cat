package com.chatcat.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** 文本块的引用列表与 BlockEntity.payload 的互转 */
object CitationCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(citations: List<Citation>): String? {
        if (citations.isEmpty()) return null
        return buildJsonArray {
            citations.distinctBy { it.url }.forEach { citation ->
                add(buildJsonObject {
                    put("url", citation.url)
                    put("title", citation.title)
                })
            }
        }.toString()
    }

    fun decode(type: String, payload: String?): List<Citation> {
        if (type != "text" || payload.isNullOrBlank() || !payload.trimStart().startsWith("[")) {
            return emptyList()
        }
        val array = runCatching { json.parseToJsonElement(payload) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = (item["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Citation(
                url = url,
                title = (item["title"] as? JsonPrimitive)?.contentOrNull,
            )
        }
    }
}
