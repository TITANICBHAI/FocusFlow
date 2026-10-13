package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.ui.home.FocusFlowInternalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch

@Composable
fun RestrictedSettingsBanner(forceVisible: Boolean? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var restricted by remember { mutableStateOf(false) }
    var installer by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            val repository = UsageStatsRepository(context)
            restricted = repository.isRestrictedSettingsBlocked()
            installer = repository.getInstallerPackage()
        }
    }
    LaunchedEffect(Unit) {
        refresh()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!(forceVisible ?: restricted)) return
    FocusFlowInternalCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 16.dp,
        contentPadding = 12.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.tbtechs.focusflow.ui.home.RefBlue.copy(alpha = 0.15f)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Permission toggle is locked by Android", fontSize = 15.scaledSp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("One-time unlock needed before Accessibility can be turned on.", fontSize = 11.scaledSp)
                }
            }
            Text(
                "Android 13+ may grey out sensitive toggles for apps installed outside trusted app stores. " +
                    "This is an Android security feature, not a FocusFlow error." +
                    (installerLabel(installer)?.let { " $it." } ?: ""),
                fontSize = 11.scaledSp,
                lineHeight = 17.scaledSp,
            )
            Text("Quick fix — takes about 10 seconds:", fontSize = 13.scaledSp, fontWeight = FontWeight.SemiBold)
            Text(
                "1. Tap Open App Info below.\n" +
                    "2. Tap the three-dot menu in the top-right corner.\n" +
                    "3. Tap Allow restricted settings.\n" +
                    "4. Return here — the Accessibility and Device Admin toggles will work now.",
                fontSize = 11.scaledSp,
                lineHeight = 17.scaledSp,
            )
            FocusFlowPrimaryButton(
                text = "Open App Info",
                icon = Icons.Outlined.OpenInNew,
                onClick = {
                    scope.launch {
                        UsageStatsRepository(context).openAppInfoSettings()
                    }
                },
            )
            FocusFlowSecondaryButton(
                text = if (expanded) "Hide details" else "Why is this needed?",
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                onClick = { expanded = !expanded },
            )
            if (expanded) {
                Text("What is this?", fontSize = 13.scaledSp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Starting in Android 13, Google added Restricted Settings to stop sideloaded apps " +
                        "from quietly granting powerful permissions like Accessibility. The toggle is " +
                        "greyed out because Android is asking you to confirm that you trust this app. " +
                        "Once you allow it from App Info, the unlock stays available for this install.",
                )
                Text("Why does it work on Samsung without this?", fontSize = 13.scaledSp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Samsung One UI often handles this flow more leniently. Pixel, Oppo, OnePlus, " +
                        "Realme, Xiaomi, Vivo, Motorola, and Nothing may enforce it strictly.",
                )
                Text("How do I avoid this on the next install?", fontSize = 13.scaledSp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Install FocusFlow from Google Play, Samsung Galaxy Store, Oppo or Realme App Market, " +
                        "Xiaomi GetApps, Vivo App Store, or Huawei AppGallery. These trusted installers " +
                        "usually skip this unlock step.",
                )
            }
        }
    }
}

private fun installerLabel(packageName: String?): String? =
    when (packageName) {
        null -> "FocusFlow was installed from an unknown source"
        "com.android.vending", "com.google.android.feedback" -> null
        "com.sec.android.app.samsungapps" -> "FocusFlow was installed via Samsung Galaxy Store"
        "com.heytap.market", "com.oppo.market" -> "FocusFlow was installed via Oppo App Market"
        "com.xiaomi.market" -> "FocusFlow was installed via Xiaomi GetApps"
        "com.bbk.appstore" -> "FocusFlow was installed via Vivo App Store"
        "com.huawei.appmarket" -> "FocusFlow was installed via Huawei AppGallery"
        "cm.aptoide.pt", "com.aptoide.uploader" -> "FocusFlow was installed via Aptoide"
        "com.uptodown.installer", "com.uptodown" -> "FocusFlow was installed via Uptodown"
        "org.fdroid.fdroid" -> "FocusFlow was installed via F-Droid"
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller" -> "FocusFlow was installed from an APK file"
        else -> "FocusFlow was installed via $packageName"
    }
