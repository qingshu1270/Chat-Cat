package com.chatcat.data.remote

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 上传前压缩图片：长边 ≤1568 px、体积 ≤3 MB，超过才重新编码为 JPEG。
 * 官方对图片有体积与分辨率限制，压缩可以显著减少上传时间与 token 消耗。
 */
object ImageCompressor {

    const val MAX_DIMENSION = 1568
    const val MAX_BYTES = 3 * 1024 * 1024L
    private const val QUALITY = 85

    /**
     * 返回压缩后的临时文件；不需要压缩或压缩失败时返回 null（调用方继续用原件）。
     * 临时文件由调用方负责删除。
     */
    suspend fun compress(
        resolver: ContentResolver,
        uri: Uri,
        cacheDir: File,
        originalSize: Long,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

            val longest = maxOf(width, height)
            val needsResize = longest > MAX_DIMENSION
            val needsShrink = originalSize > MAX_BYTES
            // 小图且不超限：不处理，避免无谓的画质损失
            if (!needsResize && !needsShrink) return@runCatching null

            var sample = 1
            while (longest / sample > MAX_DIMENSION * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }

            // JPEG 不支持透明：先铺白底，避免截图/图标出现黑块
            val opaque = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaled, 0f, 0f, null)
            }

            val file = File.createTempFile("upload_", ".jpg", cacheDir)
            file.outputStream().use { out ->
                opaque.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            opaque.recycle()
            file.takeIf { it.length() > 0 && it.length() < originalSize }
        }.getOrNull()
    }
}
