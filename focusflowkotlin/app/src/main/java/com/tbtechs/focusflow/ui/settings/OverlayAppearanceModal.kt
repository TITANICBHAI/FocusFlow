package com.tbtechs.focusflow.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.di.AppModule
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

    fun syncQuotes(next: List<String>) {
        scope.launch {
            runCatching {
                AppModule.blockOverlayController.setCustomQuotes(JSONArray(next).toString())
            }.onSuccess {
                quotes = next
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
                        body = "Please grant photo access in device Settings, then try again.",
                        showSettings = true,
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Overlay Appearance") },
                    actions = {
                        TextButton(onClick = onClose) { Text("Close") }
                    },
                )
            },
        ) { padding ->
            if (loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                    Text("Loading overlay settings", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    item {
                        OverlaySection(
                            title = "Background image",
                            description = "Pick an image from your gallery for the block overlay. Leave it empty to use the built-in dark gradient.",
                        ) {
                            if (wallpaperPath.isBlank()) {
                                Text(
                                    "Using built-in gradient background",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = wallpaperPath,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TextButton(onClick = ::removeWallpaper) { Text("Remove") }
                                }
                            }
                            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                                Text(if (wallpaperPath.isBlank()) "Pick from gallery" else "Change image")
                            }
                        }
                    }
                    item {
                        OverlaySection(
                            title = "Custom quotes",
                            description = "These rotate randomly on the overlay. Leave this list empty to use the built-in focus quotes.",
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = draftQuote,
                                    onValueChange = { draftQuote = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Type a motivating quote") },
                                    minLines = 1,
                                    maxLines = 4,
                                )
                                Button(
                                    enabled = draftQuote.trim().isNotEmpty(),
                                    onClick = {
                                        val quote = draftQuote.trim()
                                        when {
                                            quote.isEmpty() -> Unit
                                            quote in quotes -> message = OverlayMessage("Duplicate quote", "This quote is already in your list.")
                                            else -> {
                                                syncQuotes(quotes + quote)
                                                draftQuote = ""
                                            }
                                        }
                                    },
                                ) { Text("Add") }
                            }
                            if (quotes.isEmpty()) {
                                Text(
                                    "No custom quotes — built-in pool active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(quotes, key = { index, quote -> "$index-$quote" }) { index, quote ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "“$quote”",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { syncQuotes(quotes.filterIndexed { itemIndex, _ -> itemIndex != index }) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Changes apply immediately. The next block overlay will use these settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    message?.let { notice ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(notice.title) },
            text = { Text(notice.body) },
            confirmButton = {
                if (notice.showSettings) {
                    Button(onClick = { message = null; openAppSettings() }) { Text("Open settings") }
                } else {
                    Button(onClick = { message = null }) { Text("OK") }
                }
            },
            dismissButton = if (notice.showSettings) {
                { TextButton(onClick = { message = null }) { Text("Cancel") } }
            } else null,
        )
    }
}

@Composable
private fun OverlaySection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
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
