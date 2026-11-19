package com.chatcat.domain.model

/**
 * 导入合并的去重口径。
 *
 * 官方导出包与本机导入都会原样保留「标题 + 创建时间」，因此用这两个字段的组合判断会话是否已存在：
 * 同一份备份重复导入不会产生副本，改动过消息的旧会话也不会被覆盖。
 * 抽成纯函数便于单测，不依赖 Room / Android。
 */
object ArchiveMerge {

    /** 会话指纹：标题去首尾空白并忽略大小写，拼上创建时间（毫秒） */
    fun fingerprint(title: String, createdAt: Long): String =
        "${title.trim().lowercase()}|$createdAt"

    /**
     * 从导入会话里挑出本机还没有的，返回其下标（保持原顺序）。
     * 同一批导入内部的重复也只保留第一条。
     */
    fun newIndices(
        incoming: List<Pair<String, Long>>,
        existing: List<Pair<String, Long>>,
    ): List<Int> {
        val seen = existing.mapTo(HashSet()) { fingerprint(it.first, it.second) }
        val out = mutableListOf<Int>()
        incoming.forEachIndexed { index, item ->
            if (seen.add(fingerprint(item.first, item.second))) out += index
        }
        return out
    }
}
