package com.tbtechs.focusflow.ui.launcher

import android.app.WallpaperManager
import android.content.Intent
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.images.BuiltinWallpapers
import com.tbtechs.focusflow.data.images.PhoneWallpaper
import com.tbtechs.focusflow.data.images.WallpaperLoader
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowTextScale
import com.tbtechs.focusflow.ui.theme.scaledSp
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

    // The phone's own wallpaper screen, offered when the lock screen did not take.
    var pendingPicker by remember { mutableStateOf<Intent?>(null) }

    fun applyToPhone(flags: Int) {
        offerPhone = false
        scope.launch {
            working = true
            val outcome = withContext(Dispatchers.IO) {
                // Screen-sized picture, home and lock set separately, each one checked.
                val bitmap = runCatching {
                    PhoneWallpaper.screenBitmap(context, customPath, builtinId)
                }.getOrNull() ?: return@withContext null
                val result = runCatching {
                    PhoneWallpaper.apply(context, bitmap, flags)
                }.getOrNull() ?: return@withContext null
                val picker = if (result.needsSystemPicker) {
                    runCatching { PhoneWallpaper.systemPickerIntent(context, bitmap) }.getOrNull()
                } else {
                    null
                }
                result to picker
            }
            working = false
            if (outcome == null) {
                Toast.makeText(context, "Couldn't set the phone wallpaper.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, PhoneWallpaper.message(outcome.first), Toast.LENGTH_LONG).show()
                pendingPicker = outcome.second
            }
        }
    }

    val tiles = remember {
        listOf(WallpaperTileInfo(ADAPTIVE_ID, "Adaptive", "Follows your phone · best at 70%")) +
            BuiltinWallpapers.all.map { WallpaperTileInfo(it.id, it.name, "Best at ${it.bestAt}%") }
    }
    val hasWallpaperToShare = customOn || builtinId.isNotEmpty()
    val screenScale = settings.screenTextScales[
        RouteTextScaleContext.screenScaleKey(
            Routes.DEFENSE,
            RouteTextScaleContext.DEFENSE_LAUNCHER_WALLPAPER_SCREEN,
        ),
    ] ?: LocalFocusFlowTextScale.current

    CompositionLocalProvider(LocalFocusFlowTextScale provides screenScale) {
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
                        Text("‹ Back", color = BrandPrimary, fontSize = 15.scaledSp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wallpaper", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                        Text("For your FocusFlow launcher", fontSize = 12.scaledSp, color = DarkTextMuted)
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
                    // 1. Closable, and once closed it stays closed.
                    if (!settings.wallpaperMatchCardDismissed) {
                        item {
                            InfoCard(
                                title = "Make your phone match",
                                body = "Recents, switching between apps and the lock screen are drawn by your phone's own launcher. Pick a wallpaper below and you can set the same one there too.",
                                onDismiss = {
                                    settingsViewModel.updateSettings { s -> s.copy(wallpaperMatchCardDismissed = true) }
                                },
                            )
                        }
                    }
                    // 2. Glassiness.
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
                                    fontSize = 14.scaledSp,
                                    color = DarkTextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (glassDraft.toInt() == 0) "0% · plain black" else "${glassDraft.toInt()}%",
                                    fontSize = 12.scaledSp,
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
                                "0% is plain black. Above that your wallpaper shows in full, and the panels get clearer the higher you go. Long-press the launcher's home screen to try it live.",
                                fontSize = 11.scaledSp,
                                color = DarkTextMuted,
                                lineHeight = 15.scaledSp,
                            )
                        }
                    }
                    // 3. The wallpapers.
                    item {
                        SectionLabel("Wallpapers")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Drawn by the app, so there is nothing to download. Tap one to use it.",
                            fontSize = 12.scaledSp,
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
                                        onClick = {
                                            val id = if (tile.id == ADAPTIVE_ID) "" else tile.id
                                            // Tapping the one already in use offers the phone again.
                                            if (selectedId == tile.id) {
                                                if (hasWallpaperToShare) offerPhone = true
                                            } else {
                                                chooseBuiltin(id)
                                            }
                                        },
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    // 4. Your own picture: its own row, not dressed up as a wallpaper.
                    item {
                        SectionLabel("From your gallery")
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
                                .clickable(onClickLabel = "Choose a photo") { gallery.launch("image/*") }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBorder),
                                contentAlignment = Alignment.Center,
                            ) {
                                val preview = customPreview
                                if (customOn && preview != null) {
                                    Image(
                                        bitmap = preview,
                                        contentDescription = "Your photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("+", color = DarkTextSecondary, fontSize = 20.scaledSp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Your photo",
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary,
                                    fontSize = 14.scaledSp,
                                )
                                Text(
                                    if (customOn) "In use · tap to change"
                                    else "Pick one from your gallery. It stays in the app's private storage.",
                                    fontSize = 12.scaledSp,
                                    color = DarkTextSecondary,
                                )
                            }
                            if (customOn) {
                                TextButton(onClick = { chooseBuiltin("") }) {
                                    Text("Remove", color = Color(0xFFEF4444), fontSize = 13.scaledSp)
                                }
                            }
                            Text("›", color = DarkTextSecondary, fontSize = 22.scaledSp)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        // The "use it on your phone too" choice, as a sheet from the bottom.
        if (offerPhone && hasWallpaperToShare) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { offerPhone = false },
                sheetState = sheetState,
                containerColor = DarkBackground,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Use it on your phone too?",
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    // The warning is not closable: it is the one thing the person must know.
                    Text(
                        "This replaces your current phone wallpaper, and FocusFlow can't bring the old one back.",
                        fontSize = 13.scaledSp,
                        color = DarkTextSecondary,
                        lineHeight = 18.scaledSp,
                    )
                    // The explanation is optional, closable, and stays closed.
                    if (!settings.wallpaperPhoneNoteDismissed) {
                        InfoCard(
                            title = "Why do this?",
                            body = "The same picture on your home and lock screens makes Recents and app switching match FocusFlow.",
                            onDismiss = {
                                settingsViewModel.updateSettings { s -> s.copy(wallpaperPhoneNoteDismissed = true) }
                            },
                        )
                    }
                    Button(
                        onClick = { applyToPhone(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    ) {
                        Text("Home + lock screen", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { applyToPhone(WallpaperManager.FLAG_SYSTEM) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Home screen only", color = BrandPrimary)
                    }
                    TextButton(
                        onClick = { offerPhone = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Not now", color = DarkTextSecondary)
                    }
                }
            }
        }

        // Offered only when the phone did not clearly accept the lock screen wallpaper.
        pendingPicker?.let { picker ->
            AlertDialog(
                onDismissRequest = { pendingPicker = null },
                containerColor = DarkCard,
                titleContentColor = DarkTextPrimary,
                textContentColor = DarkTextSecondary,
                title = { Text("Finish in the system picker", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Your phone's own wallpaper screen opens with this picture. Choose Home, Lock or both there.",
                        fontSize = 13.scaledSp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingPicker = null
                        try {
                            context.startActivity(picker)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Couldn't open the system picker.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Open system picker", color = BrandPrimary, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPicker = null }) {
                        Text("Not now", color = DarkTextSecondary)
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
}

@Composable
private fun InfoCard(title: String, body: String, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss == null) 14.dp else 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = DarkTextPrimary, fontSize = 14.scaledSp)
            Text(body, fontSize = 12.scaledSp, color = DarkTextSecondary, lineHeight = 17.scaledSp)
        }
        if (onDismiss != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Dismiss", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = DarkTextSecondary, fontSize = 14.scaledSp)
            }
        }
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
                    Text("✓", color = Color.White, fontSize = 13.scaledSp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 13.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(hint, fontSize = 11.scaledSp, color = DarkTextMuted)
    }
}
