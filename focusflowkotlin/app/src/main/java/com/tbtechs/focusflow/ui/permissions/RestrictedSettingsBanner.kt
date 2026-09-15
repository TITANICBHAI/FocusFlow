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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RestrictedSettingsBanner(forceVisible: Boolean? = null) {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        restricted = withContext(Dispatchers.IO) { UsageStatsRepository(context).isRestrictedSettingsBlocked() }
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
            Text("Android 13+ may grey out sensitive toggles for apps installed outside the Play Store. This is an Android security feature, not a FocusFlow error.")
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
                Text("Open the three-dot menu in App Info and choose Allow restricted settings. Then return to FocusFlow and retry Accessibility. This unlock stays available for this install.")
            }
        }
    }
}
