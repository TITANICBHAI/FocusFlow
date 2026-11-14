package com.tbtechs.focusflow.ui.launcher

import android.app.WallpaperManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.images.BuiltinWallpapers
import com.tbtechs.focusflow.data.images.WallpaperLoader
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADAPTIVE_ID = "adaptive"

private class WallpaperTileInfo(
    val id: String,
    val title: String,
    val hint: String,
)

/**
 * Wallpaper chooser for the FocusFlow launcher: the app's own wallpapers, or a picture of
 * your own. Whichever you choose, it offers to set the same picture on the phone's home and
 * lock screens too. Recents and app switching are drawn by the phone's own launcher, so a
 * matching wallpaper is what makes them feel like part of FocusFlow.
 */
@Composable
internal fun LauncherWallpaperScreen(
    settingsViewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    val customPath = settings.launcherWallpaperUri?.takeIf { it.isNotBlank() }
    val builtinId = BuiltinWallpapers.byId(settings.launcherBuiltinWallpaper)?.id.orEmpty()
    val customOn = customPath != null
    val selectedId = when {
        customOn -> "custom"
        builtinId.isNotEmpty() -> builtinId
        else -> ADAPTIVE_ID
    }
    var offerPhone by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var glassDraft by remember(settings.launcherGlassLevel) {
        mutableStateOf(settings.launcherGlassLevel.toFloat())
    }
    val previews = remember { mutableStateMapOf<String, ImageBitmap>() }
    var customPreview by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        // Previews are drawn one after another so the screen opens straight away.
        withContext(Dispatchers.Default) {
            val palette = BuiltinWallpapers.phonePalette(context)
            previews[ADAPTIVE_ID] = BuiltinWallpapers.adaptive(palette, 270, 585).asImageBitmap()
            BuiltinWallpapers.all.forEach { spec ->
                previews[spec.id] = BuiltinWallpapers.render(spec, 270, 585).asImageBitmap()
            }
        }
    }
    LaunchedEffect(customPath) {
        customPreview = if (customPath == null) null else withContext(Dispatchers.IO) {
            runCatching { WallpaperLoader.decode(context, customPath)?.asImageBitmap() }.getOrNull()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                working = true
                // Validated and re-encoded into app-private storage, so it survives restarts.
                val stored = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            AppModule.wallpaperStore.ingest(input).getOrThrow()
                        }
                    }.getOrNull()
                }
                working = false
                if (stored != null) {
                    settingsViewModel.updateSettings { s ->
                        s.copy(launcherWallpaperUri = stored, launcherBuiltinWallpaper = "")
                    }
                    offerPhone = true
                } else {
                    Toast.makeText(context, "That image could not be used as a wallpaper.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun chooseBuiltin(id: String) {
        val hadCustom = customOn
        settingsViewModel.updateSettings { s ->
            s.copy(launcherBuiltinWallpaper = id, launcherWallpaperUri = null)
        }
        if (hadCustom) scope.launch(Dispatchers.IO) { AppModule.wallpaperStore.clear() }
        if (id.isNotEmpty()) offerPhone = true
    }

    fun applyToPhone(flags: Int) {
        offerPhone = false
        scope.launch {
            working = true
            val ok = withContext(Dispatchers.IO) { setPhoneWallpaper(context, customPath, builtinId, flags) }
            working = false
            Toast.makeText(
                context,
                if (ok) "Phone wallpaper updated. Recents and the lock screen now match."
                else "Couldn't set the phone wallpaper.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val tiles = remember {
        listOf(WallpaperTileInfo(ADAPTIVE_ID, "Adaptive", "Follows your phone · best at 70%")) +
            BuiltinWallpapers.all.map { WallpaperTileInfo(it.id, it.name, "Best at ${it.bestAt}%") }
    }
    val hasWallpaperToShare = customOn || builtinId.isNotEmpty()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text("‹ Back", color = BrandPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wallpaper", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    Text("For your FocusFlow launcher", fontSize = 12.sp, color = DarkTextMuted)
                }
                if (working) {
                    CircularProgressIndicator(
                        color = BrandPrimary,
                        modifier = Modifier.size(22.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    InfoCard(
                        "Make your phone match",
                        "Recents, switching between apps and the lock screen are drawn by your phone's own launcher. Use the same picture there and they feel like part of FocusFlow. Whatever you choose below, you can set it on your phone too.",
                    )
                }
                item {
                    SectionLabel("YOUR OWN PICTURE")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .border(
                                if (customOn) 2.dp else 1.dp,
                                if (customOn) BrandPrimary else DarkBorder,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 90.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkBorder),
                        ) {
                            customPreview?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (customOn) "Your picture" else "Use a photo from your gallery",
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                                fontSize = 14.sp,
                            )
                            Text(
                                if (customOn) "Try 40–70% glass." else "It stays on your phone, in the app's private storage.",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { gallery.launch("image/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                ) { Text(if (customOn) "Change" else "Choose", fontSize = 13.sp, color = Color.White) }
                                if (customOn) {
                                    TextButton(onClick = { chooseBuiltin("") }) {
                                        Text("Remove", color = Color(0xFFEF4444), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionLabel("FOCUSFLOW WALLPAPERS")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Drawn by the app, so there is nothing to download. Each one shows the glass level it looks best at.",
                        fontSize = 12.sp,
                        color = DarkTextMuted,
                    )
                }
                items(tiles.chunked(2), key = { row -> row.first().id }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { tile ->
                            Box(modifier = Modifier.weight(1f)) {
                                WallpaperTile(
                                    title = tile.title,
                                    hint = tile.hint,
                                    selected = selectedId == tile.id,
                                    preview = previews[tile.id],
                                    onClick = { chooseBuiltin(if (tile.id == ADAPTIVE_ID) "" else tile.id) },
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Glassiness",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (glassDraft.toInt() == 0) "0% · plain black" else "${glassDraft.toInt()}%",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                            )
                        }
                        Slider(
                            value = glassDraft,
                            onValueChange = { glassDraft = it },
                            onValueChangeFinished = {
                                val level = glassDraft.toInt().coerceIn(0, 100)
                                settingsViewModel.updateSettings { s -> s.copy(launcherGlassLevel = level) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary),
                        )
                        Text(
                            "Open the launcher to see the result. Long-press the home screen there to try the slider live.",
                            fontSize = 11.sp,
                            color = DarkTextMuted,
                        )
                    }
                }
                if (hasWallpaperToShare) {
                    item {
                        Button(
                            onClick = { offerPhone = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        ) {
                            Text("Set this on my phone too…", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (offerPhone && hasWallpaperToShare) {
        AlertDialog(
            onDismissRequest = { offerPhone = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Use it on your phone too?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The same picture on your home and lock screens makes Recents and app switching match FocusFlow. It replaces your current phone wallpaper, and FocusFlow can't bring the old one back.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { applyToPhone(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }) {
                    Text("Home + lock screen", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { applyToPhone(WallpaperManager.FLAG_SYSTEM) }) {
                        Text("Home only", color = BrandPrimary)
                    }
                    TextButton(onClick = { offerPhone = false }) {
                        Text("Not now", color = DarkTextSecondary)
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = DarkTextSecondary)
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = DarkTextPrimary, fontSize = 14.sp)
        Text(body, fontSize = 12.sp, color = DarkTextSecondary, lineHeight = 17.sp)
    }
}

@Composable
private fun WallpaperTile(
    title: String,
    hint: String,
    selected: Boolean,
    preview: ImageBitmap?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) BrandPrimary else DarkBorder,
                    RoundedCornerShape(16.dp),
                ),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "$title wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(hint, fontSize = 11.sp, color = DarkTextMuted)
    }
}

/** Blocking: call off the main thread. [flags] is FLAG_SYSTEM, FLAG_LOCK or both. */
private fun setPhoneWallpaper(context: Context, customPath: String?, builtinId: String, flags: Int): Boolean {
    return try {
        val metrics = context.resources.displayMetrics
        val spec = BuiltinWallpapers.byId(builtinId)
        val bitmap = when {
            customPath != null -> WallpaperLoader.decode(context, customPath)
            spec != null -> BuiltinWallpapers.render(spec, metrics.widthPixels, metrics.heightPixels)
            else -> null
        } ?: return false
        WallpaperManager.getInstance(context.applicationContext).setBitmap(bitmap, null, true, flags)
        true
    } catch (_: Exception) {
        false
    }
}
