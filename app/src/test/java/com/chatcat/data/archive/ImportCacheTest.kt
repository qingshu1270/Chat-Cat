package com.chatcat.data.archive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 导入临时文件的清理逻辑。
 *
 * 这组用例守的是「大包导入失败后留下上百 MB 幽灵数据」这个回归：
 * 只要文件名是导入临时包，就必须能被扫出来删掉，而用户自己的文件一律不能碰。
 */
class ImportCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `只认导入流程自己产生的文件名`() {
        assertTrue(ImportCache.isImportTemp("deepseek_import_1732260000000.zip"))
        assertTrue(ImportCache.isImportTemp("chatcat_import_123456.zip"))
        assertFalse(ImportCache.isImportTemp("deepseek_data-2026-05-01.zip"))
        assertFalse(ImportCache.isImportTemp("demo-chat.zip"))
        assertFalse(ImportCache.isImportTemp("deepseek_import_1.zip.bak"))
        assertFalse(ImportCache.isImportTemp("photo.jpg"))
    }

    @Test
    fun `清扫会删除临时包并统计释放空间`() {
        val dir = tmp.newFolder()
        val stale = File(dir, "deepseek_import_1.zip").apply { writeBytes(ByteArray(2048)) }
        val temp = File(dir, "chatcat_import_9.zip").apply { writeBytes(ByteArray(1024)) }
        val userFile = File(dir, "deepseek_data-keep.zip").apply { writeBytes(ByteArray(4096)) }

        val freed = ImportCache.sweep(dir)

        assertEquals(3072L, freed)
        assertFalse(stale.exists())
        assertFalse(temp.exists())
        assertTrue("用户自己的文件不能被删", userFile.exists())
    }

    @Test
    fun `正在使用的那份不会被误删`() {
        val dir = tmp.newFolder()
        val inUse = File(dir, "deepseek_import_2.zip").apply { writeBytes(ByteArray(512)) }
        val stale = File(dir, "deepseek_import_3.zip").apply { writeBytes(ByteArray(128)) }

        val freed = ImportCache.sweep(dir, keep = inUse)

        assertEquals(128L, freed)
        assertTrue(inUse.exists())
        assertFalse(stale.exists())
    }

    @Test
    fun `目录不存在时安静返回零`() {
        assertEquals(0L, ImportCache.sweep(File(tmp.root, "not-exist")))
    }
}
