package com.tbtechs.focusflow.enforcement.customrules

import android.content.SharedPreferences

/**
 * Reads and writes the custom screen rules, stored as one JSON string in the
 * `focusday_prefs` file. The accessibility service keeps its own small cache on top of this.
 */
object CustomRuleStore {
    const val PREF_KEY = "custom_screen_rules"

    fun read(prefs: SharedPreferences): List<CustomScreenRule> =
        CustomScreenRules.fromJson(prefs.getString(PREF_KEY, "[]") ?: "[]")

    fun write(prefs: SharedPreferences, rules: List<CustomScreenRule>) {
        prefs.edit().putString(PREF_KEY, CustomScreenRules.toJson(rules)).apply()
    }

    fun setEnabled(prefs: SharedPreferences, id: String, enabled: Boolean) {
        val current = read(prefs)
        if (current.none { it.id == id }) return
        write(prefs, current.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }
}
