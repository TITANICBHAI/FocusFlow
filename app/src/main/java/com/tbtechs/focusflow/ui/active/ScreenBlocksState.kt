package com.tbtechs.focusflow.ui.active

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.customrules.CustomRuleStore

private fun enabledRuleNames(prefs: SharedPreferences): List<String> =
    runCatching { CustomRuleStore.read(prefs).filter { it.enabled }.map { it.name } }
        .getOrDefault(emptyList())

/**
 * Names of the custom rules that are switched on, kept current: the list updates the moment a
 * rule is added, removed or toggled anywhere in the app.
 */
@Composable
internal fun rememberEnabledCustomRuleNames(): List<String> {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    }
    var names by remember(prefs) { mutableStateOf(enabledRuleNames(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == CustomRuleStore.PREF_KEY) names = enabledRuleNames(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        names = enabledRuleNames(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return names
}

internal fun screenBlocksOf(settings: AppSettings, enabledRuleNames: List<String>): ScreenBlocks =
    ScreenBlocks.of(
        ruleNames = enabledRuleNames,
        youtubeShorts = settings.blockYoutubeShortsEnabled,
        instagramReels = settings.blockInstagramReelsEnabled,
        installActions = settings.blockInstallActionsEnabled,
    )
