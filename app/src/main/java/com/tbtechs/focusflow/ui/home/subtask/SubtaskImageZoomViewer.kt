package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.ui.common.SubtaskImageView
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun SubtaskImageZoomViewer(
    image: SubtaskImage,
    imageStore: SubtaskImageStore?,
    onDismiss: () -> Unit,
) {
    var scale by remember(image.asset) { mutableFloatStateOf(1f) }
    var imageOffset by remember(image.asset) { mutableStateOf(Offset.Zero) }
    var swipeDown by remember(image.asset) { mutableFloatStateOf(0f) }
    var closing by remember(image.asset) { mutableStateOf(false) }
    val description = image.alt?.takeIf(String::isNotBlank) ?: "Subtask photo"
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(image.asset) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        swipeDown = 0f
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = imageOffset.x
                        translationY = imageOffset.y + swipeDown
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Zoomable photo: $description"
                    }
                    .pointerInput(image.asset) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale <= 1.01f && nextScale <= 1.01f && zoom == 1f) {
                                if (pan.y > 0f) {
                                    swipeDown = (swipeDown + pan.y).coerceAtMost(size.height * 0.6f)
                                    if (swipeDown >= 150f && !closing) {
                                        closing = true
                                        onDismiss()
                                    }
                                } else if (pan.y < 0f) {
                                    swipeDown = 0f
                                }
                            } else {
                                swipeDown = 0f
                                if (nextScale <= 1f) {
                                    scale = 1f
                                    imageOffset = Offset.Zero
                                } else {
                                    scale = nextScale
                                    val limit = size.width * (nextScale - 1f) / 2f
                                    imageOffset = Offset(
                                        x = (imageOffset.x + pan.x).coerceIn(-limit, limit),
                                        y = (imageOffset.y + pan.y).coerceIn(-limit, limit),
                                    )
                                }
                            }
                        }
                    }
                    .pointerInput(image.asset) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1.01f) 1f else 2.5f
                                imageOffset = Offset.Zero
                                swipeDown = 0f
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                SubtaskImageView(
                    assetId = image.asset,
                    imageStore = imageStore,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    targetWidthPx = targetWidthPx,
                    animate = false,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .semantics { contentDescription = "Close full-screen image" },
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
            }
            Text(
                text = "Pinch or double-tap to zoom · Swipe down to close",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                color = Color.White,
                fontSize = 13.scaledSp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
