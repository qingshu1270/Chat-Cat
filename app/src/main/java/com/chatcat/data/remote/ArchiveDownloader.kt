package com.chatcat.data.remote

import com.chatcat.domain.model.ExportLinkParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载导出包失败的原因，UI 侧据此给中文提示与降级入口 */
enum class DownloadFailure {
    /** 链接过期 / 无权限 / 已删除 */
    EXPIRED,

    /** 超过本地可处理体积 */
    TOO_LARGE,

    /** 下载到的不是 ZIP（例如复制成了普通页面链接） */
    NOT_ARCHIVE,

    /** 网络不可用、超时、连接中断 */
    NETWORK,

    /** 服务端异常或不是官方地址 */
    UNAVAILABLE,
}

class ArchiveDownloadException(
    val reason: DownloadFailure,
    cause: Throwable? = null,
) : IOException(reason.name, cause)

/**
 * 下载官方历史导出包到本机缓存。
 *
 * 只允许 https + 官方域名；跟随重定向（导出链接通常带签名参数），
 * 限制体积并校验 ZIP magic，避免把 HTML 错误页当成备份导入。
 */
class ArchiveDownloader(private val client: OkHttpClient) {

    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull()
            ?: throw ArchiveDownloadException(DownloadFailure.UNAVAILABLE)
        // 官方导出包常落在 CDN / 对象存储域，不能只认 deepseek.com；
        // 但要求 https + 非伪装域名 + 命中导出特征，避免把任意地址当备份下载。
        if (httpUrl.scheme != "https" || !ExportLinkParser.looksDownloadable(url)) {
            throw ArchiveDownloadException(DownloadFailure.UNAVAILABLE)
        }

        val request = Request.Builder()
            .url(httpUrl)
            // 部分 CDN / WAF 会拦 okhttp 默认 UA，这里伪装成普通浏览器请求
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/122.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept", "application/zip,application/octet-stream,*/*")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ArchiveDownloadException(DownloadFailure.NETWORK, e)
        }

        try {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val reason = when (resp.code) {
                        401, 403, 404, 410 -> DownloadFailure.EXPIRED
                        else -> DownloadFailure.UNAVAILABLE
                    }
                    throw ArchiveDownloadException(reason)
                }
                val body = resp.body ?: throw ArchiveDownloadException(DownloadFailure.UNAVAILABLE)
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (contentType.contains("text/html")) {
                    throw ArchiveDownloadException(DownloadFailure.NOT_ARCHIVE)
                }
                val length = body.contentLength()
                if (length > MAX_BYTES) throw ArchiveDownloadException(DownloadFailure.TOO_LARGE)

                var total = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(dest).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = try {
                                input.read(buffer)
                            } catch (e: IOException) {
                                throw ArchiveDownloadException(DownloadFailure.NETWORK, e)
                            }
                            if (read <= 0) break
                            total += read
                            if (total > MAX_BYTES) {
                                throw ArchiveDownloadException(DownloadFailure.TOO_LARGE)
                            }
                            out.write(buffer, 0, read)
                            if (length > 0) {
                                // 只在百分比变化时回调，避免每个 8KB 块都触发重组把界面拖死
                                val percent = (total * 100 / length).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress((percent / 100f).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                if (total == 0L) throw ArchiveDownloadException(DownloadFailure.UNAVAILABLE)
            }
        } catch (e: Throwable) {
            dest.delete()
            throw e
        }

        if (!isZip(dest)) {
            dest.delete()
            throw ArchiveDownloadException(DownloadFailure.NOT_ARCHIVE)
        }
        onProgress(1f)
        dest
    }

    private fun isZip(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val head = ByteArray(2)
            stream.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    companion object {
        /** 官方导出包为文本 JSON，正常远小于此；超出改走本地文件导入 */
        const val MAX_BYTES = 512L * 1024 * 1024
    }
}
