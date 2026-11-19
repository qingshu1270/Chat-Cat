package com.chatcat.domain.model

/**
 * 可见消息解析：
 * - 官方 App 的「重新生成」为同一提问保留多个回答，用 `‹ 2/3 ›` 切换；
 * - 「修改」会产生同一提问的多个版本（`parentId` 指向上一版提问），同样可切换；
 * - 旧数据（回答没有 parentId）仍按「提问后面那条」顺序配对。
 */
object VersionResolver {

    fun resolve(all: List<UiMessage>): List<UiMessage> {
        val userById = all.filter { it.role == Role.USER }.associateBy { it.id }
        val rootMemo = HashMap<String, String>()

        // 版本链的根：沿 parentId 一路向上，直到没有更早的提问版本
        fun rootOf(message: UiMessage): String {
            rootMemo[message.id]?.let { return it }
            var current = message
            val guard = mutableSetOf<String>()
            while (true) {
                val parentId = current.parentId ?: break
                val parent = userById[parentId] ?: break
                if (!guard.add(current.id)) break
                current = parent
            }
            rootMemo[message.id] = current.id
            return current.id
        }

        val usersByRoot = all
            .filter { it.role == Role.USER }
            .groupBy { rootOf(it) }
            .mapValues { (_, list) -> list.sortedBy { it.seq } }

        val replies = all
            .filter { it.role == Role.ASSISTANT && it.parentId != null }
            .groupBy { it.parentId.orEmpty() }

        val result = ArrayList<UiMessage>(all.size)
        all.forEachIndexed { index, message ->
            if (message.role != Role.USER) return@forEachIndexed
            if (!message.active) return@forEachIndexed

            val versionGroup = usersByRoot[rootOf(message)].orEmpty()
            val versionIndex = versionGroup.indexOfFirst { it.id == message.id }
            result += if (versionGroup.size > 1 && versionIndex >= 0) {
                message.copy(versionIndex = versionIndex + 1, versionCount = versionGroup.size)
            } else {
                message
            }

            val versions = replies[message.id]
            if (!versions.isNullOrEmpty()) {
                val sorted = versions.sortedBy { it.seq }
                val activeIndex = sorted.indexOfFirst { it.active }.takeIf { it >= 0 }
                    ?: sorted.lastIndex
                result += sorted[activeIndex].copy(
                    parentId = message.id,
                    versionIndex = activeIndex + 1,
                    versionCount = sorted.size,
                )
            } else {
                // 旧数据：回答没有 parentId，仍按「提问后面那条」顺序配对
                val next = all.getOrNull(index + 1)
                if (next != null &&
                    next.role == Role.ASSISTANT &&
                    next.parentId == null &&
                    next.active
                ) {
                    result += next
                }
            }
        }
        return result
    }
}
