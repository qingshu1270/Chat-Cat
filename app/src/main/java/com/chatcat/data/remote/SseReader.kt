package com.chatcat.data.remote

import okio.BufferedSource

/**
 * SSE 分帧：按空行切帧，容忍注释行（keep-alive）与多行 data。
 * 与 chat-demo 的 sse.ts 语义一致。
 */
class SseReader(private val source: BufferedSource) {

    data class Frame(val event: String?, val data: String)

    fun frames(): Sequence<Frame> = sequence {
        var event: String? = null
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty() || event != null) {
                    yield(Frame(event, data.toString()))
                }
                event = null
                data.clear()
                continue
            }
            if (line.startsWith(":")) continue
            when {
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        if (data.isNotEmpty()) yield(Frame(event, data.toString()))
    }
}
