package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.ui.navigation.DefenseIcons
import com.tbtechs.focusflow.ui.navigation.FocusTabIcons
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlin.math.roundToInt

private const val MIN_TEXT_SCALE_PERCENT = 80
private const val MAX_TEXT_SCALE_PERCENT = 150
private const val TEXT_SCALE_SLIDER_STEPS = MAX_TEXT_SCALE_PERCENT - MIN_TEXT_SCALE_PERCENT - 1

private data class TabTextScale(
    val title: String,
    val route: String,
    val icon: ImageVector,
    val scale: Float?,
    val screens: List<TextSizeTarget>,
    val update: (Float?) -> Unit,
)

internal data class TextSizeTarget(
    val tabRoute: String,
    val tabTitle: String,
    val title: String,
    val screenId: String,
    val description: String,
) {
    val scaleKey: String
        get() = RouteTextScaleContext.screenScaleKey(tabRoute, screenId)
}

@Composable
internal fun TextSizeSection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    selectedScreen: TextSizeTarget?,
    onSelectScreen: (TextSizeTarget) -> Unit,
) {
    val generalPercent = scaleToPercent(settings.generalTextScale)
    var expandedTab by remember { mutableStateOf<String?>(null) }
    val tabScales = listOf(
        TabTextScale(
            title = "Focus",
            route = Routes.FOCUS,
            icon = FocusTabIcons.Active,
            scale = settings.focusTextScale,
            screens = listOf(
                TextSizeTarget(Routes.FOCUS, "Focus", "Active session", Routes.ACTIVE, "The active-session screen opened from Focus."),
                TextSizeTarget(Routes.FOCUS, "Focus", "Permissions", Routes.PERMISSIONS, "The permissions screen opened from Focus."),
                TextSizeTarget(Routes.FOCUS, "Focus", "Standalone block", RouteTextScaleContext.FOCUS_STANDALONE_SETUP_SCREEN, "App selection and timing for setting up a standalone block."),
                TextSizeTarget(Routes.FOCUS, "Focus", "Active standalone block", RouteTextScaleContext.FOCUS_STANDALONE_PANEL_SCREEN, "The active block countdown, app list, and quick time controls."),
                TextSizeTarget(Routes.FOCUS, "Focus", "Focus extension", RouteTextScaleContext.FOCUS_EXTENSION_SCREEN, "The time-extension choices for a scheduled focus session."),
            ),
        ) { value ->
            onUpdate(settings.copy(focusTextScale = value))
        },
        TabTextScale(
            title = "Schedule",
            route = Routes.HOME,
            icon = Icons.Outlined.CalendarMonth,
            scale = settings.homeTextScale,
            screens = listOf(
                TextSizeTarget(
                    Routes.HOME,
                    "Schedule",
                    "Task cards & active banner",
                    RouteTextScaleContext.HOME_TASK_LIST_SCREEN,
                    "Task cards, status labels, and the running or ended task banner.",
                ),
                TextSizeTarget(Routes.HOME, "Schedule", "Quick Add task", RouteTextScaleContext.HOME_QUICK_ADD_SCREEN, "The quick-add task form."),
                TextSizeTarget(Routes.HOME, "Schedule", "Task details", RouteTextScaleContext.HOME_TASK_DETAILS_SCREEN, "The task details sheet."),
                TextSizeTarget(Routes.HOME, "Schedule", "Edit task", RouteTextScaleContext.HOME_EDIT_TASK_SCREEN, "The task editing form."),
            ),
        ) { value ->
            onUpdate(settings.copy(homeTextScale = value))
        },
        TabTextScale(
            title = "Defense",
            route = Routes.DEFENSE,
            icon = DefenseIcons.ActiveFilled,
            scale = settings.defenseTextScale,
            screens = listOf(
                TextSizeTarget(Routes.DEFENSE, "Defense", "Always-On", Routes.ALWAYS_ON, "Always-On app controls."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Block Defense", Routes.BLOCK_DEFENSE, "Standalone block controls."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Home launcher setup", Routes.HOME_LAUNCHER_SETUP, "Launcher setup and app visibility."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Keyword blocking", Routes.KEYWORD_BLOCKER, "Keyword blocker controls."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Password protection", Routes.PASSWORD_PROTECTION, "Defense PIN and protection settings."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "VPN block list", Routes.VPN_BLOCK_LIST, "Apps managed by VPN blocking."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Active session", Routes.ACTIVE, "The active-session screen opened from Defense."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Permissions", Routes.PERMISSIONS, "The permissions screen opened from Defense."),
            ),
        ) { value ->
            onUpdate(settings.copy(defenseTextScale = value))
        },
        TabTextScale(
            title = "Stats",
            route = Routes.STATS,
            icon = Icons.Outlined.BarChart,
            scale = settings.statsTextScale,
            screens = listOf(
                TextSizeTarget(Routes.STATS, "Stats", "Reports", Routes.REPORTS, "The reports list opened from Stats."),
                TextSizeTarget(Routes.STATS, "Stats", "Report details", Routes.REPORT, "A report opened from Stats."),
            ),
        ) { value ->
            onUpdate(settings.copy(statsTextScale = value))
        },
        TabTextScale(
            title = "Settings",
            route = Routes.SETTINGS,
            icon = Icons.Outlined.Settings,
            scale = settings.settingsTextScale,
            screens = listOf(
                TextSizeTarget(Routes.SETTINGS, "Settings", "How to Use", Routes.SETTINGS_HOW_TO_USE, "The Settings guide."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Profile", Routes.USER_PROFILE, "Your profile screen."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Changelog", Routes.CHANGELOG, "The app changelog."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Privacy Policy", Routes.PRIVACY_POLICY, "The privacy policy screen."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Terms of Service", Routes.TERMS_OF_SERVICE, "The terms screen."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Permissions", Routes.PERMISSIONS, "The permissions screen opened from Settings."),
            ),
        ) { value ->
            onUpdate(settings.copy(settingsTextScale = value))
        },
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (selectedScreen != null) {
            val tab = tabScales.first { it.route == selectedScreen.tabRoute }
            val screenScale = settings.screenTextScales[selectedScreen.scaleKey]
            val inheritedScale = tab.scale ?: settings.generalTextScale
            val updatedScales = settings.screenTextScales.toMutableMap()
            SettingsSliderRow(
                title = "Text size",
                description = screenScale?.let { "Custom size · ${scaleToPercent(it)}%" }
                    ?: "Matches ${tab.title} · ${scaleToPercent(inheritedScale)}%",
                valuePercent = scaleToPercent(screenScale ?: inheritedScale),
                onValueChange = { percent ->
                    updatedScales[selectedScreen.scaleKey] = percentToScale(percent)
                    onUpdate(settings.copy(screenTextScales = updatedScales))
                },
                onValueChangeFinished = {},
                onReset = if (screenScale == null) null else {
                    {
                        updatedScales.remove(selectedScreen.scaleKey)
                        onUpdate(settings.copy(screenTextScales = updatedScales))
                    }
                },
                resetLabel = "Use ${tab.title}",
            )
        } else {
            SettingsSliderRow(
                title = "General",
                description = "Default size for tabs without a custom size.",
                valuePercent = generalPercent,
                onValueChange = { percent ->
                    onUpdate(settings.copy(generalTextScale = percentToScale(percent)))
                },
                onValueChangeFinished = {},
            )
            HorizontalDivider(color = DarkBorder, thickness = 1.dp)
            Text(
                text = "TAB SIZES",
                fontSize = 11.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextSecondary,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
            )
            tabScales.forEachIndexed { index, tab ->
                val expanded = expandedTab == tab.title
                val customPercent = tab.scale?.let(::scaleToPercent)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTab = if (expanded) null else tab.title }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tab.title,
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            text = customPercent?.let { "Custom size · $it%" }
                                ?: "Matches General · $generalPercent%",
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "Collapse ${tab.title}" else "Expand ${tab.title}",
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (expanded) {
                SettingsSliderRow(
                    title = "${tab.title} text size",
                    description = if (tab.scale == null) "Matches General" else "Custom size",
                    valuePercent = customPercent ?: generalPercent,
                    onValueChange = { percent -> tab.update(percentToScale(percent)) },
                    onValueChangeFinished = {},
                    onReset = if (tab.scale == null) null else {
                        { tab.update(null) }
                    },
                )
                    tab.screens.forEach { screen ->
                        TextSizeScreenRow(
                            target = screen,
                            scale = settings.screenTextScales[screen.scaleKey],
                            inheritedScale = tab.scale ?: settings.generalTextScale,
                            onClick = { onSelectScreen(screen) },
                        )
                    }
                }
                if (index != tabScales.lastIndex) {
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TextSizeScreenRow(
    target: TextSizeTarget,
    scale: Float?,
    inheritedScale: Float,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.title,
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.Medium,
                color = DarkTextPrimary,
            )
            Text(
                text = scale?.let { "Custom size · ${scaleToPercent(it)}%" }
                    ?: "Matches tab · ${scaleToPercent(inheritedScale)}%",
                fontSize = 11.scaledSp,
                color = DarkTextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = "Customize ${target.title}",
            tint = DarkTextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    description: String,
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Use General",
) {
    var sliderValue by remember(valuePercent) { mutableStateOf(valuePercent) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    lineHeight = 16.scaledSp,
                )
            }
            if (onReset != null) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(resetLabel, fontSize = 12.scaledSp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = sliderValue.toFloat(),
                onValueChange = { newValue ->
                    sliderValue = newValue.roundToInt().coerceIn(
                        MIN_TEXT_SCALE_PERCENT,
                        MAX_TEXT_SCALE_PERCENT,
                    )
                },
                onValueChangeFinished = {
                    if (sliderValue != valuePercent) onValueChange(sliderValue)
                    onValueChangeFinished()
                },
                valueRange = MIN_TEXT_SCALE_PERCENT.toFloat()..MAX_TEXT_SCALE_PERCENT.toFloat(),
                steps = TEXT_SCALE_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = BrandPrimary,
                    activeTrackColor = BrandPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$title text size" },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$sliderValue%",
                fontSize = 13.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp),
            )
        }
    }
}

private fun scaleToPercent(scale: Float): Int =
    (scale * 100f).roundToInt().coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT)

private fun percentToScale(percent: Int): Float =
    percent.coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT) / 100f