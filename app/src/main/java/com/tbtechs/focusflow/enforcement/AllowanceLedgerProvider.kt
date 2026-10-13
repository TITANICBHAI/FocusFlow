package com.tbtechs.focusflow.enforcement

import android.content.Context

/** Owns the single application-wide ledger instance. */
internal object AllowanceLedgerProvider {
    @Volatile
    private var instance: AllowanceLedger? = null

    fun get(context: Context): AllowanceLedger {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: AllowanceLedger(
                SharedPreferencesAllowanceStore(
                    context.applicationContext.getSharedPreferences(
                        AllowanceLedger.PREFS_NAME,
                        Context.MODE_PRIVATE,
                    ),
                ),
            ).also { instance = it }
        }
    }
}
