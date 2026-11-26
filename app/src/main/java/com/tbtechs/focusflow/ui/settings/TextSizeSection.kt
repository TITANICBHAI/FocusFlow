package com.tbtechs.focusflow.ui.settings

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
            title = stringResource(R.string.tab_focus),
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
            title = stringResource(R.string.tab_schedule),
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
                TextSizeTarget(Routes.HOME, "Schedule", "Subtask details", RouteTextScaleContext.HOME_SUBTASK_DETAILS_SCREEN, "The subtask detail screen."),
                TextSizeTarget(Routes.HOME, "Schedule", "Edit task", RouteTextScaleContext.HOME_EDIT_TASK_SCREEN, "The task editing form."),
            ),
        ) { value ->
            onUpdate(settings.copy(homeTextScale = value))
        },
        TabTextScale(
            title = stringResource(R.string.tab_defense),
            route = Routes.DEFENSE,
            icon = DefenseIcons.ActiveFilled,
            scale = settings.defenseTextScale,
            screens = listOf(
                TextSizeTarget(Routes.DEFENSE, "Defense", "Always-On", Routes.ALWAYS_ON, "Always-On app controls."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Block Defense", Routes.BLOCK_DEFENSE, "Standalone block controls."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Home launcher setup", Routes.HOME_LAUNCHER_SETUP, "Launcher setup and app visibility."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Launcher wallpaper", RouteTextScaleContext.DEFENSE_LAUNCHER_WALLPAPER_SCREEN, "The Wallpaper screen opened from Home launcher setup."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Focus tools picker", RouteTextScaleContext.DEFENSE_FOCUS_TOOLS_PICKER_SCREEN, "The app list for Focus tools in Home launcher setup."),
                TextSizeTarget(Routes.DEFENSE, "Defense", "Hidden apps picker", RouteTextScaleContext.DEFENSE_HIDDEN_APPS_PICKER_SCREEN, "The app list for Hidden apps in Home launcher setup."),
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
            title = stringResource(R.string.tab_stats),
            route = Routes.STATS,
            icon = Icons.Outlined.BarChart,
            scale = settings.statsTextScale,
            screens = listOf(
                TextSizeTarget(Routes.STATS, "Stats", "Reports", Routes.REPORTS, "The reports list opened from Stats."),
                TextSizeTarget(Routes.STATS, "Stats", "Report details", Routes.REPORT, "A report opened from Stats."),
                TextSizeTarget(Routes.STATS, "Stats", "Extra · Overview", RouteTextScaleContext.STATS_EXTRAS_OVERVIEW_SCREEN, "Extra's Overview tab, whichever period is chosen."),
                TextSizeTarget(Routes.STATS, "Stats", "Extra · Apps", RouteTextScaleContext.STATS_EXTRAS_APPS_SCREEN, "Extra's Apps tab, whichever period is chosen."),
                TextSizeTarget(Routes.STATS, "Stats", "Extra · Habits", RouteTextScaleContext.STATS_EXTRAS_HABITS_SCREEN, "Extra's Habits tab, whichever period is chosen."),
            ),
        ) { value ->
            onUpdate(settings.copy(statsTextScale = value))
        },
        TabTextScale(
            title = stringResource(R.string.tab_settings),
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
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules", Routes.CUSTOM_RULES, "The custom screen rules hub."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · List", Routes.CUSTOM_RULES_LIST, "The list of custom screen rules."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · Edit", Routes.CUSTOM_RULE_EDIT, "Editing one custom screen rule."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · Capture", Routes.CUSTOM_RULE_CAPTURE, "Capturing a screen for a custom rule."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · Pick", Routes.CUSTOM_RULE_PICK, "Picking the screen a custom rule applies to."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · Transfer", Routes.CUSTOM_RULES_TRANSFER, "Importing and exporting custom rules."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Custom rules · Guide", Routes.CUSTOM_RULES_GUIDE, "The custom rules guide."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Usage data", Routes.SETTINGS_USAGE_DATA, "The usage data screen."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "FocusFlow file guide", Routes.FOCUSFLOW_FILE_GUIDE, "The guide to .focusflow files."),
                TextSizeTarget(Routes.SETTINGS, "Settings", "Text size", Routes.TEXT_SIZE_SETTINGS, "This Text Size screen itself."),
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
                title = stringResource(R.string.settings_text_size_2),
                description = screenScale?.let { stringResource(R.string.settings_custom_size_pct, scaleToPercent(it)) }
                    ?: stringResource(R.string.settings_matches_target_pct, tab.title, scaleToPercent(inheritedScale)),
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
                resetLabel = stringResource(R.string.settings_use_target, tab.title),
            )
        } else {
            SettingsSliderRow(
                title = stringResource(R.string.settings_general),
                description = stringResource(R.string.settings_default_size_for_tabs_without),
                valuePercent = generalPercent,
                onValueChange = { percent ->
                    onUpdate(settings.copy(generalTextScale = percentToScale(percent)))
                },
                onValueChangeFinished = {},
            )
            HorizontalDivider(color = DarkBorder, thickness = 1.dp)
            Text(
                text = stringResource(R.string.settings_tab_sizes),
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
                            text = customPercent?.let { stringResource(R.string.settings_custom_size_pct, it) }
                                ?: stringResource(R.string.settings_matches_target_pct, stringResource(R.string.settings_general), generalPercent),
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
                        contentDescription = stringResource(if (expanded) R.string.settings_collapse_target else R.string.settings_expand_target, tab.title),
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (expanded) {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_text_size_3, tab.title),
                    description = stringResource(if (tab.scale == null) R.string.settings_matches_general else R.string.settings_custom_size),
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
                text = scale?.let { stringResource(R.string.settings_custom_size_pct, scaleToPercent(it)) }
                    ?: stringResource(R.string.settings_matches_tab_pct, scaleToPercent(inheritedScale)),
                fontSize = 11.scaledSp,
                color = DarkTextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.settings_customize, target.title),
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
    resetLabel: String = stringResource(R.string.settings_use_target, stringResource(R.string.settings_general)),
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
            val sliderDescription = stringResource(R.string.settings_text_size_3, title)
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
                    .semantics { contentDescription = sliderDescription },
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