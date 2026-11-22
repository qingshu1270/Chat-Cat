package com.chatcat.data.archive

import com.chatcat.domain.model.AttachmentPolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入流程产生的临时文件管理。
 *
 * 「从 DeepSeek 全量导入」会把导出包下载到 `cacheDir`，单流导入入口会把流转存成临时文件；
 * 这些文件动辄上百 MB，只能靠流程里的 finally 删除。一旦进程被系统杀掉（大包 OOM 是最常见的
 * 一种），或者用户从导入页直接退出，删除就没人做了——它会一直躺在应用私有目录里，
 * 用户在系统设置里既看不到也删不掉，纯粹占用空间。
 *
 * 所以这里的策略是「三处兜底」：
 * 1. 应用启动时扫一遍（清理上次进程被杀留下的残骸）；
 * 2. 每次开始下载前扫一遍（顺手清掉更早的失败残留）；
 * 3. 离开导入页 / 取消 / 失败时立即删除当前这一份。
 */
object ImportCache {

    /** 下载得到的导出包 */
    private const val DOWNLOAD_PREFIX = "deepseek_import_"

    /** 单流导入入口的转存文件 */
    private const val TEMP_PREFIX = "chatcat_import_"

    private const val SUFFIX = ".zip"

    fun isImportTemp(name: String): Boolean =
        name.endsWith(SUFFIX) && (name.startsWith(DOWNLOAD_PREFIX) || name.startsWith(TEMP_PREFIX))

    /**
     * 删除目录下的导入临时文件，返回释放的字节数。
     * [keep] 传当前仍在使用的那份（若存在），不会被误删。
     */
    fun sweep(dir: File, keep: File? = null): Long {
        val files = dir.listFiles() ?: return 0L
        var freed = 0L
        files.forEach { file ->
            if (!file.isFile || !isImportTemp(file.name)) return@forEach
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            val size = file.length()
            if (file.delete()) freed += size
        }
        return freed
    }

    /** 同时清理缓存目录与 JVM 临时目录（单流转存可能落在后者） */
    suspend fun sweepAll(cacheDir: File, keep: File? = null): Long = withContext(Dispatchers.IO) {
        var freed = sweep(cacheDir, keep)
        val tmp = File(System.getProperty("java.io.tmpdir").orEmpty())
        if (tmp.absolutePath != cacheDir.absolutePath && tmp.isDirectory) {
            freed += sweep(tmp, keep)
        }
        freed
    }

    fun formatSize(bytes: Long): String = AttachmentPolicy.formatSize(bytes)
}
