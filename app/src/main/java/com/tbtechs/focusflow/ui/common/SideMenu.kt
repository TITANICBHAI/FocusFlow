package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.navigation.DefenseIcons
import com.tbtechs.focusflow.ui.navigation.FocusTabIcons
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions

data class SideMenuItem(
    val route: String,
    val label: String,
    val icon: @Composable (selected: Boolean) -> Unit,
)

@Composable
fun SideMenu(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val items = listOf(
        SideMenuItem(Routes.HOME, "Home") { _ ->
            Icon(Icons.Outlined.Home, contentDescription = null)
        },
        SideMenuItem(Routes.FOCUS, "Focus") { selected ->
            Icon(
                imageVector = if (selected) FocusTabIcons.Active else FocusTabIcons.Inactive,
                contentDescription = null,
            )
        },
        SideMenuItem(Routes.STATS, "Stats") { _ ->
            Icon(Icons.Outlined.BarChart, contentDescription = null)
        },
        SideMenuItem(Routes.SETTINGS, "Settings") { _ ->
            Icon(Icons.Outlined.Settings, contentDescription = null)
        },
        SideMenuItem(Routes.DEFENSE, "Defense") { selected ->
            Icon(
                imageVector = if (selected) DefenseIcons.ActiveFilled else DefenseIcons.Outline,
                contentDescription = null,
            )
        },
    )
    val dimensions = LocalFocusFlowDimensions.current
    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = dimensions.drawerMaxWidth)
            .navigationBarsPadding(),
        drawerShape = MaterialTheme.shapes.large,
    ) {
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
                val selected = currentRoute == item.route
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = selected,
                    onClick = {
                        onNavigate(item.route)
                        onClose()
                    },
                    icon = { item.icon(selected) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            NavigationDrawerItem(
                label = { Text("How to use") },
                selected = RouteTextScaleContext.routeBase(currentRoute) == Routes.HOW_TO_USE,
                onClick = {
                    onNavigate(Routes.HOW_TO_USE)
                    onClose()
                },
                icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text("Privacy & Terms") },
                selected = currentRoute?.startsWith(Routes.PRIVACY_POLICY) == true,
                onClick = {
                    onNavigate("${Routes.PRIVACY_POLICY}?revisit=true")
                    onClose()
                },
                icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}