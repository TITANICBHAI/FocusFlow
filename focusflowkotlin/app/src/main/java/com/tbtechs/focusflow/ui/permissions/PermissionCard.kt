package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun PermissionCard(
    permission: PermissionDefinition,
    status: PermissionStatus,
    expanded: Boolean,
    busy: Boolean,
    showTroubleshoot: Boolean = false,
    onToggle: () -> Unit,
    onGrant: () -> Unit,
    onTroubleshoot: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (status == PermissionStatus.GRANTED) Icons.Outlined.CheckCircle else Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(permission.title, style = MaterialTheme.typography.titleSmall)
                        if (permission.optional) FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Optional") })
                    }
                    Text(permission.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        when (status) {
                            PermissionStatus.GRANTED -> "Ready"
                            PermissionStatus.DENIED -> if (permission.optional) "Not set up" else "Missing"
                            PermissionStatus.UNKNOWN -> "Checking…"
                        },
                        color = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                androidx.compose.material3.IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Details")
                }
            }
            if (status != PermissionStatus.GRANTED && !expanded) {
                Button(onClick = onGrant, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(if (busy) "Opening…" else "Give access")
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Why this is needed", style = MaterialTheme.typography.labelLarge)
                    Text(permission.whyNeeded, style = MaterialTheme.typography.bodySmall)
                    if (status != PermissionStatus.GRANTED) {
                        Text("Without this permission:", style = MaterialTheme.typography.labelLarge)
                        permission.brokenWithout.forEach { item ->
                            Row {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(item, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onGrant, enabled = !busy) { Text("Open settings") }
                            if (showTroubleshoot) {
                                OutlinedButton(onClick = onTroubleshoot) {
                                    Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                                    Text("Troubleshoot")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
