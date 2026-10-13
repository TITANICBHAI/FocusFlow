package com.tbtechs.focusflow.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.home.FocusFlowInternalCard
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.home.RefBackground
import com.tbtechs.focusflow.ui.home.RefBorder
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Full-screen editor for the existing block-overlay preferences. It writes via
 * [BlockOverlayController], the same enforcement-facing preference owner read
 * by BlockOverlayActivity; the UI never creates a parallel setting store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayAppearanceModal(
    visible: Boolean,
    onClose: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftQuote by remember { mutableStateOf("") }
    var wallpaperPath by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<OverlayMessage?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { AppModule.blockOverlayController.getOverlaySettings() }
            .onSuccess { raw ->
                val state = parseOverlayState(raw)
                quotes = state.quotes
                wallpaperPath = state.wallpaperPath
            }
            .onFailure {
                message = OverlayMessage(
                    title = "Overlay settings unavailable",
                    body = "FocusFlow could not read the current block-overlay settings.",
                )
            }
        loading = false
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
    }

    fun syncQuotes(next: List<String>, onSaved: () -> Unit = {}) {
        scope.launch {
            runCatching {
                AppModule.blockOverlayController.setCustomQuotes(JSONArray(next).toString())
            }.onSuccess {
                quotes = next
                onSaved()
            }.onFailure {
                message = OverlayMessage("Could not save quotes", "Your overlay quote changes were not saved.")
            }
        }
    }

    fun removeWallpaper() {
        scope.launch {
            runCatching { AppModule.blockOverlayController.clearOverlayWallpaper() }
                .onSuccess { wallpaperPath = "" }
                .onFailure {
                    message = OverlayMessage("Could not remove image", "The overlay background image could not be removed.")
                }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val target = withContext(Dispatchers.IO) {
                        File(context.filesDir, "focusflow_overlay_${System.currentTimeMillis()}").also { file ->
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("The selected image cannot be read")
                            input.use { source -> file.outputStream().use { destination -> source.copyTo(destination) } }
                        }
                    }
                    AppModule.blockOverlayController.setOverlayWallpaper(target.absolutePath)
                    target.absolutePath
                }.onSuccess { path ->
                    wallpaperPath = path
                }.onFailure {
                    message = OverlayMessage(
                        title = "Could not pick image",
                        body = "The selected image could not be imported. Try choosing it again from your gallery.",
                        showSettings = false,
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(RefBackground).navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Overlay Appearance", modifier = Modifier.weight(1f), color = RefText, fontSize = 18.scaledSp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("Close", color = RefSecondary, fontSize = 13.scaledSp) }
            }
            if (loading) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = RefText, modifier = Modifier.size(24.dp))
                    Text("Loading overlay settings", color = RefSecondary, fontSize = 13.scaledSp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OverlaySection(
                            title = "BACKGROUND IMAGE",
                            description = "Pick an image from your gallery for the block overlay. Leave it empty to use the built-in dark gradient.",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, tint = RefSecondary, modifier = Modifier.size(20.dp))
                                Text(
                                    if (wallpaperPath.isBlank()) "Using built-in gradient background" else wallpaperPath,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    color = RefSecondary,
                                    fontSize = 13.scaledSp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (wallpaperPath.isNotBlank()) {
                                    TextButton(onClick = ::removeWallpaper) { Text("Remove", color = Color(0xFFF87171), fontSize = 13.scaledSp) }
                                }
                            }
                            FocusFlowPrimaryButton(
                                text = if (wallpaperPath.isBlank()) "Pick from gallery" else "Change image",
                                icon = Icons.Outlined.Image,
                                onClick = { pickImage.launch("image/*") },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                    item {
                        OverlaySection(
                            title = "CUSTOM QUOTES",
                            description = "These rotate randomly on the overlay. Leave this list empty to use the built-in focus quotes.",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BasicTextField(
                                    value = draftQuote,
                                    onValueChange = { draftQuote = it },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = RefText, fontSize = 13.scaledSp),
                                    maxLines = 5,
                                    decorationBox = { inner ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 120.dp)
                                                .clip(RoundedCornerShape(10.dp)).background(RefCard)
                                                .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            if (draftQuote.isBlank()) Text("Type a motivating quote", color = RefMuted, fontSize = 13.scaledSp)
                                            inner()
                                        }
                                    },
                                )
                                Box(
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (draftQuote.trim().isNotEmpty()) BrandPrimary else RefMuted.copy(alpha = 0.35f))
                                        .clickable(enabled = draftQuote.trim().isNotEmpty()) {
                                            val quote = draftQuote.trim()
                                            when {
                                                quote in quotes -> message = OverlayMessage("Duplicate quote", "This quote is already in your list.")
                                                else -> syncQuotes(quotes + quote) { draftQuote = "" }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Add quote", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (quotes.isEmpty()) {
                                Text("No custom quotes — built-in pool active", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = RefSecondary, fontSize = 11.scaledSp)
                            }
                        }
                    }
                    itemsIndexed(quotes, key = { index, quote -> "$index-$quote" }) { index, quote ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("“$quote”", modifier = Modifier.weight(1f), color = RefText, fontSize = 13.scaledSp, lineHeight = 20.scaledSp, fontStyle = FontStyle.Italic)
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete quote",
                                tint = RefSecondary,
                                modifier = Modifier.size(20.dp).clickable { syncQuotes(quotes.filterIndexed { itemIndex, _ -> itemIndex != index }) },
                            )
                        }
                    }
                    item {
                        Text("Changes apply immediately. The next block overlay will use these settings.", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = RefSecondary, fontSize = 11.scaledSp, lineHeight = 16.scaledSp)
                    }
                }
            }
        }
    }

    message?.let { notice ->
        Dialog(
            onDismissRequest = { message = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FocusFlowModalCard(modifier = Modifier.fillMaxWidth().padding(16.dp), radius = 16.dp, contentPadding = 16.dp) {
                Text(notice.title, color = RefText, fontSize = 18.scaledSp, fontWeight = FontWeight.Bold)
                Text(notice.body, color = RefSecondary, fontSize = 11.scaledSp, lineHeight = 17.scaledSp, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (notice.showSettings) {
                        FocusFlowSecondaryButton(text = "Cancel", onClick = { message = null }, modifier = Modifier.weight(1f))
                        FocusFlowPrimaryButton(text = "Open settings", onClick = { message = null; openAppSettings() }, modifier = Modifier.weight(1f))
                    } else {
                        FocusFlowPrimaryButton(text = "OK", onClick = { message = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 11.scaledSp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.scaledSp,
            color = RefSecondary,
        )
        Text(
            description,
            fontSize = 11.scaledSp,
            lineHeight = 16.scaledSp,
            color = RefSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        FocusFlowInternalCard(
            modifier = Modifier.fillMaxWidth(),
            radius = 16.dp,
            contentPadding = 0.dp,
        ) {
            content()
        }
    }
}

private data class OverlayState(
    val quotes: List<String>,
    val wallpaperPath: String,
)

private data class OverlayMessage(
    val title: String,
    val body: String,
    val showSettings: Boolean = false,
)

private fun parseOverlayState(raw: String): OverlayState {
    val json = JSONObject(raw)
    val quotesJson = json.optString("quotesJson")
    val quotes = runCatching {
        val array = JSONArray(quotesJson)
        List(array.length()) { index -> array.optString(index).trim() }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())
    return OverlayState(quotes = quotes, wallpaperPath = json.optString("wallpaperPath"))
}
