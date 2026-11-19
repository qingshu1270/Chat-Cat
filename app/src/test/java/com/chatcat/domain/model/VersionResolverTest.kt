package com.chatcat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionResolverTest {

    private fun text(id: String) = listOf(
        UiBlock(id = "$id-b", ordinal = 0, type = BlockType.TEXT, text = id, closed = true),
    )

    private fun user(id: String, seq: Long) = UiMessage(
        id = id,
        role = Role.USER,
        status = MessageStatus.DONE,
        blocks = text(id),
        seq = seq,
    )

    private fun assistant(
        id: String,
        seq: Long,
        parentId: String?,
        active: Boolean = true,
    ) = UiMessage(
        id = id,
        role = Role.ASSISTANT,
        status = MessageStatus.DONE,
        blocks = text(id),
        seq = seq,
        parentId = parentId,
        active = active,
    )

    @Test
    fun `legacy messages without parent are untouched`() {
        val all = listOf(user("u1", 1), assistant("a1", 2, null), user("u2", 3), assistant("a2", 4, null))
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1", "a1", "u2", "a2"), resolved.map { it.id })
    }

    @Test
    fun `only active version is visible and counted`() {
        val all = listOf(
            user("u1", 1),
            assistant("a1", 2, "u1", active = false),
            assistant("a2", 3, "u1", active = true),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1", "a2"), resolved.map { it.id })
        val reply = resolved.last()
        assertEquals(2, reply.versionCount)
        assertEquals(2, reply.versionIndex)
        assertEquals("u1", reply.parentId)
    }

    @Test
    fun `switching active version changes the visible answer`() {
        val all = listOf(
            user("u1", 1),
            assistant("a1", 2, "u1", active = true),
            assistant("a2", 3, "u1", active = false),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1", "a1"), resolved.map { it.id })
        assertEquals(1, resolved.last().versionIndex)
        assertEquals(2, resolved.last().versionCount)
    }

    @Test
    fun `edited branch hides the old question and its answer`() {
        val all = listOf(
            user("u1", 1).copy(active = false),
            assistant("a1", 2, "u1").copy(active = false),
            user("u1x", 3),
            assistant("a1x", 4, "u1x"),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1x", "a1x"), resolved.map { it.id })
    }

    @Test
    fun `later turns keep their own answer`() {
        val all = listOf(
            user("u1", 1),
            assistant("a1", 2, "u1"),
            user("u2", 3),
            assistant("a2", 4, "u2"),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1", "a1", "u2", "a2"), resolved.map { it.id })
        assertEquals(1, resolved.last().versionCount)
    }

    @Test
    fun `edited question exposes version count`() {
        val all = listOf(
            user("u1", 1).copy(active = false),
            assistant("a1", 2, "u1").copy(active = false),
            user("u1x", 3).copy(parentId = "u1"),
            assistant("a1x", 4, "u1x"),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1x", "a1x"), resolved.map { it.id })
        assertEquals(2, resolved[0].versionCount)
        assertEquals(2, resolved[0].versionIndex)
    }

    @Test
    fun `switching back shows the previous question and its answer`() {
        val all = listOf(
            user("u1", 1),
            assistant("a1", 2, "u1"),
            user("u1x", 3).copy(parentId = "u1", active = false),
            assistant("a1x", 4, "u1x"),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1", "a1"), resolved.map { it.id })
        assertEquals(1, resolved[0].versionIndex)
        assertEquals(2, resolved[0].versionCount)
    }

    @Test
    fun `chained edits collapse into one version group`() {
        val all = listOf(
            user("u1", 1).copy(active = false),
            assistant("a1", 2, "u1").copy(active = false),
            user("u1b", 3).copy(parentId = "u1", active = false),
            assistant("a1b", 4, "u1b").copy(active = false),
            user("u1c", 5).copy(parentId = "u1b"),
            assistant("a1c", 6, "u1c"),
        )
        val resolved = VersionResolver.resolve(all)
        assertEquals(listOf("u1c", "a1c"), resolved.map { it.id })
        assertEquals(3, resolved[0].versionCount)
        assertEquals(3, resolved[0].versionIndex)
    }
}
