package com.chatcat.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class ToolSource(
    val title: String,
    val url: String,
    val siteName: String? = null,
    val snippet: String? = null,
)

/** 思维链里的一条工具步骤（搜索 / 打开网页 / 阅读链接） */
data class ToolInfo(
    val type: String,
    val status: String,
    val queries: List<String> = emptyList(),
    val sources: List<ToolSource> = emptyList(),
    val title: String? = null,
    val url: String? = null,
) {
    val isSearch: Boolean get() = type.contains("search")
    val isFetch: Boolean get() = type.contains("fetch") || type == "open" || type == "read_link"
}

/**
 * 把服务端工具块或归档片段解析为可展示的步骤。
 * 只认 HTTP/HTTPS 链接，避免渲染 javascript: 等协议。
 */
object ToolParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(type: String, payload: String?): ToolInfo? {
        if (payload.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }
            .getOrNull() ?: return null
        val kind = type.ifBlank { root["type"].asString().orEmpty() }

        return when {
            kind == "server_tool_use" -> {
                val input = root["input"] as? JsonObject
                ToolInfo(
                    type = type,
                    status = "wip",
                    queries = input?.get("queries")?.stringList().orEmpty(),
                    url = input?.get("url")?.asString(),
                    title = null,
                )
            }

            kind == "web_search_tool_result" || kind == "TOOL_SEARCH" -> {
                val content = root["content"] as? JsonArray
                val sources = content.orEmptyArray().mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val url = item["url"].asString() ?: return@mapNotNull null
                    if (!isWebUrl(url)) return@mapNotNull null
                    ToolSource(
                        title = item["title"].asString() ?: url,
                        url = url,
                        siteName = item["site_name"].asString(),
                        snippet = item["snippet"].asString(),
                    )
                }
                ToolInfo(
                    type = "web_search_tool_result",
                    status = if (sources.isEmpty() && content == null) "wip" else "done",
                    queries = root["queries"]?.stringList().orEmpty(),
                    sources = sources,
                )
            }

            kind == "web_fetch_tool_result" || kind == "TOOL_OPEN" || kind == "READ_LINK" -> {
                val content = root["content"] as? JsonObject
                val result = content?.takeIf { it["type"].asString() == "web_fetch_result" }
                val url = result?.get("url").asString() ?: root["url"].asString()
                val title = result?.get("title").asString() ?: root["title"].asString()
                ToolInfo(
                    type = "web_fetch_tool_result",
                    status = if (result != null) "done" else "failed",
                    url = url?.takeIf(::isWebUrl),
                    title = title,
                )
            }

            else -> null
        }
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    private fun JsonArray?.orEmptyArray(): List<kotlinx.serialization.json.JsonElement> =
        this?.toList().orEmpty()

    private fun kotlinx.serialization.json.JsonElement?.asString(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun kotlinx.serialization.json.JsonElement?.stringList(): List<String> =
        (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
