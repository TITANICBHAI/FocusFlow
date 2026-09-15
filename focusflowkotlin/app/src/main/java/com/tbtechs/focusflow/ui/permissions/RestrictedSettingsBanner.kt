package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Permission toggle is locked by Android")
                    Text("One-time unlock needed before Accessibility can be turned on.")
                }
            }
            Text(
                "Android 13+ may grey out sensitive toggles for apps installed outside the Play Store. " +
                    "This is an Android security feature, not a FocusFlow error." +
                    (installerLabel(installer)?.let { " $it." } ?: ""),
            )
            Text("Quick fix — takes about 10 seconds:")
            Text(
                "1. Tap Open App Info below.\n" +
                    "2. Tap the three-dot menu in the top-right corner.\n" +
                    "3. Tap Allow restricted settings.\n" +
                    "4. Return here — the Accessibility toggle will work now.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        UsageStatsRepository(context).openAppInfoSettings()
                    }
                }) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Text("Open App Info")
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    Text(if (expanded) "Hide details" else "Why is this needed?")
                }
            }
            if (expanded) {
                Text(
                    "Starting in Android 13, this protects sensitive permissions for sideloaded apps. " +
                        "Once you allow restricted settings from App Info, the unlock stays available for this install.",
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
