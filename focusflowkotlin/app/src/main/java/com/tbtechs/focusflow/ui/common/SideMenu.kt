package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class SideMenuItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun SideMenu(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val items = listOf(
        SideMenuItem("home", "Home", Icons.Outlined.Home),
        SideMenuItem("focus", "Focus", Icons.Outlined.CalendarMonth),
        SideMenuItem("stats", "Stats", Icons.Outlined.Analytics),
        SideMenuItem("settings", "Settings", Icons.Outlined.Settings),
        SideMenuItem("defense", "Defense", Icons.Outlined.Shield),
    )
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 18.dp),
        ) {
            Text(
                "FocusFlow",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            )
            items.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = currentRoute == item.route,
                    onClick = {
                        onNavigate(item.route)
                        onClose()
                    },
                    icon = { Icon(item.icon, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            NavigationDrawerItem(
                label = { Text("How to use") },
                selected = currentRoute == "how_to_use",
                onClick = {
                    onNavigate("how_to_use")
                    onClose()
                },
                icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Privacy & Terms") },
                selected = currentRoute == "privacy_policy",
                onClick = {
                    onNavigate("privacy_policy")
                    onClose()
                },
                icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}