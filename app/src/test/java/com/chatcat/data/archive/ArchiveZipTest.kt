package com.chatcat.data.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归档流式遍历的回归测试。
 *
 * 这里守住的是「导入大包不 OOM」的地基：遍历器一次只持有一条条目的字节，
 * 且必须正确处理官方包的外层目录前缀、目录条目与大小未知的条目。
 */
class ArchiveZipTest {

    private fun zip(vararg entries: Pair<String, ByteArray>, prefix: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(if (prefix != null) "$prefix$name" else name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun storedZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                val crc = CRC32().apply { update(bytes) }
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun names(input: ByteArray): List<String> {
        val out = mutableListOf<String>()
        ZipWalker(ByteArrayInputStream(input)).use { walker ->
            while (true) {
                val name = walker.nextEntry() ?: break
                out += name
                // 故意不读：跳过条目也要能继续走下去
            }
        }
        return out
    }

    @Test
    fun `按顺序遍历并剥离外层目录前缀`() {
        val bytes = zip(
            "manifest.json" to """{"export_format_version":3}""".toByteArray(),
            "chat_sessions/a.json" to "{}".toByteArray(),
            "chat_session_index.json" to "[]".toByteArray(),
            prefix = "deepseek_data-2026-05-01/",
        )
        assertEquals(
            listOf("manifest.json", "chat_sessions/a.json", "chat_session_index.json"),
            names(bytes),
        )
    }

    @Test
    fun `目录条目与无关文件不影响会话匹配`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 官方包可能先放一个目录条目
            zip.putNextEntry(ZipEntry("chat_sessions/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("chat_sessions/a.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("chat_sessions/a_files/pic.png"))
            zip.write(ByteArray(8))
            zip.closeEntry()
        }
        val sessions = names(out.toByteArray()).filter { ChatArchive.SESSION_PATH.matches(it) }
        assertEquals(listOf("chat_sessions/a.json"), sessions)
        // 目录条目出现在最前面，也不能把 chat_sessions/ 当成包裹目录
        assertTrue(names(out.toByteArray()).contains("chat_sessions/a.json"))
    }

    @Test
    fun `只读关心的条目也能拿到完整字节`() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val bytes = storedZip(
            "manifest.json" to """{"export_format_version":3}""".toByteArray(),
            "chat_sessions/big.json" to payload,
        )
        var manifest: String? = null
        var sessionBytes: ByteArray? = null
        ZipWalker(ByteArrayInputStream(bytes)).use { walker ->
            while (true) {
                val name = walker.nextEntry() ?: break
                when {
                    name == "manifest.json" -> manifest = walker.readCurrent().decodeToString()
                    ChatArchive.SESSION_PATH.matches(name) -> sessionBytes = walker.readCurrent()
                    // 其他条目直接跳过，不分配
                }
            }
        }
        assertTrue(manifest!!.contains("export_format_version"))
        assertTrue(payload.contentEquals(sessionBytes!!))
    }

    @Test
    fun `大小未知的条目也能正确读取`() {
        // 默认 deflate 写入：本地头里 size 记 0，遍历器应回退到动态读取而不是截断
        val payload = "内容".repeat(50_000).toByteArray()
        val bytes = zip("chat_sessions/x.json" to payload)
        var read: ByteArray? = null
        ZipWalker(ByteArrayInputStream(bytes)).use { walker ->
            while (true) {
                val name = walker.nextEntry() ?: break
                if (ChatArchive.SESSION_PATH.matches(name)) read = walker.readCurrent()
            }
        }
        assertTrue(payload.contentEquals(read!!))
    }

    @Test
    fun `读完后再取条目返回 null`() {
        val bytes = zip("manifest.json" to "{}".toByteArray())
        ZipWalker(ByteArrayInputStream(bytes)).use { walker ->
            assertEquals("manifest.json", walker.nextEntry())
            assertNull(walker.nextEntry())
            assertNull(walker.nextEntry())
        }
    }

    @Test
    fun `前缀取自首个文件条目的顶层目录`() {
        assertEquals("deepseek_data-1/", detectPrefix("deepseek_data-1/manifest.json"))
        assertEquals("", detectPrefix("manifest.json"))
        assertEquals("chat_sessions/", detectPrefix("chat_sessions/a.json"))
        assertEquals("b.json", stripPrefix("a/b.json", "a/"))
        assertEquals("b.json", stripPrefix("b.json", "a/"))
    }

    @Test
    fun `首个文件就在 chat_sessions 下时不会被误剥前缀`() {
        val bytes = zip("chat_sessions/x.json" to "{}".toByteArray())
        assertEquals(listOf("chat_sessions/x.json"), names(bytes))
    }

    @Test
    fun `会话路径正则只认 chat_sessions 下的一层 json`() {
        assertTrue(ChatArchive.SESSION_PATH.matches("chat_sessions/abc.json"))
        assertFalse(ChatArchive.SESSION_PATH.matches("chat_sessions/abc_files/pic.png"))
        assertFalse(ChatArchive.SESSION_PATH.matches("chat_session_index.json"))
    }
}
