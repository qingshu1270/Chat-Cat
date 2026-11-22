package com.chatcat.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按目标尺寸降采样解码本地图片；不依赖第三方图片库，避免为缩略图引入整套加载链。
 * 失败（权限失效、文件已删）返回 null，由调用方显示占位。
 */
@Composable
fun rememberUriImage(uri: String?, targetSize: Int = 512, version: Long = 0L): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(initialValue = null, uri, targetSize, version) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(uri)
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > targetSize ||
                        bounds.outHeight / sample > targetSize
                    ) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    resolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return state
}

/** 圆角图片；无原件时显示灰色占位 */
@Composable
fun AttachmentImage(
    uri: String?,
    modifier: Modifier = Modifier,
    corner: Int = 12,
) {
    val bitmap = rememberUriImage(uri)
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
