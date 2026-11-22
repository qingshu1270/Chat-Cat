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

        /**
         * 风控关键词。服务端可能用 type=content_filter、message="Content Exists Risk"、
         * 中文「内容存在风险」等多种表述，这里统一兜住，避免漏判成普通失败。
         */
        private val RISK_KEYWORDS = listOf(
            "content exists risk",
            "content_filter",
            "content filter",
            "content moderation",
            "content_policy",
            "safety_check",
            "risk control",
            "violat",
            "内容存在风险",
            "内容审核",
            "内容安全",
            "敏感内容",
            "违规内容",
        )

        /** SSE error 帧 / HTTP body 是否属于内容风控 */
        fun isRiskMessage(vararg parts: String?): Boolean {
            val text = parts.filterNotNull().joinToString(" ").lowercase()
            if (text.isBlank()) return false
            return RISK_KEYWORDS.any { text.contains(it) }
        }

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
                // 风控优先：403 也可能是内容风控，先按关键词判定
                status in 400..403 && isRiskMessage(body) -> Kind.CONTENT_RISK
                status == 401 || status == 403 -> Kind.AUTH
                status == 402 -> Kind.BALANCE
                status == 429 -> Kind.RATE_LIMIT
                status == 413 -> Kind.UPLOAD_TOO_LARGE
                status in 500..599 -> Kind.SERVER_BUSY
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
