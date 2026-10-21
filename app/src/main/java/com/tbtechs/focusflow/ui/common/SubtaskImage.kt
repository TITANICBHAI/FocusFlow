package com.tbtechs.focusflow.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberSubtaskBitmap(
    assetId: String?,
    targetWidthPx: Int,
    imageStore: SubtaskImageStore?,
): State<ImageBitmap?> {
    val bucketWidth = targetWidthPx.bucketWidth()
    return produceState<ImageBitmap?>(
        initialValue = null,
        assetId,
        bucketWidth,
        imageStore,
    ) {
        value = null
        if (assetId.isNullOrBlank() || imageStore == null) {
            return@produceState
        }

        val cacheKey = "$assetId@$bucketWidth"
        value = imageCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            imageCache.get(cacheKey) ?: decodeSubtaskBitmap(
                imageStore.file(assetId),
                bucketWidth,
            )?.also { imageCache.put(cacheKey, it) }
        }
    }
}

@Composable
fun SubtaskImageView(
    assetId: String,
    imageStore: SubtaskImageStore?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthPx: Int,
    animate: Boolean = true,
) {
    val image by rememberSubtaskBitmap(assetId, targetWidthPx, imageStore)
    if (animate) {
        Crossfade(
            targetState = image,
            animationSpec = tween(durationMillis = 150),
            label = "subtask-image-crossfade",
            modifier = modifier,
        ) { bitmap ->
            if (bitmap == null) {
                SubtaskImageUnavailable(contentDescription)
            } else {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else if (image == null) {
        Box(modifier = modifier) {
            SubtaskImageUnavailable(contentDescription)
        }
    } else {
        androidx.compose.foundation.Image(
            bitmap = image!!,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

@Composable
private fun SubtaskImageUnavailable(description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) {
                contentDescription = "Image unavailable: $description"
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Image unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun Int.bucketWidth(): Int {
    val positive = coerceAtLeast(1)
    return ((positive + WIDTH_BUCKET - 1) / WIDTH_BUCKET) * WIDTH_BUCKET
}

private fun decodeSubtaskBitmap(file: java.io.File?, targetWidthPx: Int): ImageBitmap? {
    file ?: return null
    if (!file.isFile) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            target = targetWidthPx,
        )
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        decoded.asImageBitmap()
    }.getOrNull()
}

private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    val longestEdge = maxOf(width, height)
    while (longestEdge / (sample * 2) >= target) {
        sample *= 2
    }
    return sample
}

private val imageCache: LruCache<String, ImageBitmap> by lazy {
    val maxSize = (Runtime.getRuntime().maxMemory() / 8L)
        .coerceIn(1L, Int.MAX_VALUE.toLong())
        .toInt()
    object : LruCache<String, ImageBitmap>(maxSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height.toLong() * 4L)
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
    }
}

private const val WIDTH_BUCKET = 64
