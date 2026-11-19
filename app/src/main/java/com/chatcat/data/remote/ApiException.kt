package com.chatcat.data.remote

/** 统一错误分类：UI 只认 Kind，不解析英文原文。 */
class ApiException(
    val kind: Kind,
    override val message: String,
    val status: Int? = null,
    /** 服务端排查用，仅写日志，绝不展示给用户 */
    val requestId: String? = null,
) : Exception(message) {

    enum class Kind {
        AUTH,
        BALANCE,
        CONTENT_RISK,
        CONTEXT_LIMIT,
        RATE_LIMIT,
        SERVER_BUSY,
        UPLOAD_TOO_LARGE,
        PARSE_FAILED,
        INVALID_REQUEST,
        NETWORK,
        STREAM_INTERRUPTED,
        PROTOCOL,
        CANCELLED,
        UNKNOWN,
    }

    companion object {
        fun fromHttp(status: Int, body: String): ApiException {
            val serverMessage = runCatching {
                Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            }.getOrNull()
            // 原文只用于日志；界面文案由 localizedError(kind) 生成
            val message = serverMessage ?: "HTTP $status"
            val requestId = runCatching {
                Regex("\"request_id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            }.getOrNull()
            val kind = when {
                status == 401 || status == 403 -> Kind.AUTH
                status == 402 -> Kind.BALANCE
                status == 429 -> Kind.RATE_LIMIT
                status == 413 -> Kind.UPLOAD_TOO_LARGE
                status in 500..599 -> Kind.SERVER_BUSY
                status == 400 && body.contains("Content Exists Risk", ignoreCase = true) -> Kind.CONTENT_RISK
                status == 400 && (body.contains("context", true) || body.contains("too long", true)) ->
                    Kind.CONTEXT_LIMIT
                status == 400 && (body.contains("parse", true) || body.contains("document", true)) ->
                    Kind.PARSE_FAILED
                status == 400 -> Kind.INVALID_REQUEST
                else -> Kind.UNKNOWN
            }
            return ApiException(kind, message, status, requestId)
        }
    }
}
