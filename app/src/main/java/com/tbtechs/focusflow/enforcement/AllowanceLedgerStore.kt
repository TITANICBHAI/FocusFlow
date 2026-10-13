package com.tbtechs.focusflow.enforcement

import android.content.SharedPreferences

internal interface AllowanceLedgerStore {
    fun readUsageJson(): String?
    fun writeUsageJson(value: String)
}

internal class SharedPreferencesAllowanceStore(
    private val prefs: SharedPreferences,
) : AllowanceLedgerStore {
    override fun readUsageJson(): String? = prefs.getString(
        AllowanceLedger.PREF_DAILY_ALLOWANCE_USED,
        null,
    )

    override fun writeUsageJson(value: String) {
        prefs.edit().putString(AllowanceLedger.PREF_DAILY_ALLOWANCE_USED, value).apply()
    }
}
