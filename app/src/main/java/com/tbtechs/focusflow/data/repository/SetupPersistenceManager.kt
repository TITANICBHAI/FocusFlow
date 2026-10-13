package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SetupPersistenceManager
 *
 * Implements durable state management for first-run onboarding completion,
 * mirroring the archived FocusFlow setup backup pattern (setupPersistence.ts).
 *
 * Critical first-run state (privacy acceptance, onboarding completion, background
 * service consent, and protection mode) is mirrored across both the primary
 * "focusday_prefs" store and a dedicated secondary "focusday_setup_backup" store.
 *
 * Both stores use synchronous commit() writes so process death never loses completion state.
 * If the primary store is ever cleared or corrupted, the backup auto-heals it on read.
 */
class SetupPersistenceManager(context: Context) {

    companion object {
        private const val TAG = "SetupPersistence"
        const val PRIMARY_PREFS_NAME = AppBlockerAccessibilityService.PREFS_NAME
        const val BACKUP_PREFS_NAME = "focusday_setup_backup"

        const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_USER_CONSENTED_BACKGROUND_SERVICE = "user_consented_background_service"
        const val KEY_PROTECTION_MODE = "protection_mode"
    }

    private val appContext = context.applicationContext

    private val primaryPrefs: SharedPreferences
        get() = appContext.getSharedPreferences(PRIMARY_PREFS_NAME, Context.MODE_PRIVATE)

    private val backupPrefs: SharedPreferences
        get() = appContext.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)

    fun readDurableFlag(key: String): Boolean {
        val primaryRaw = primaryPrefs.all[key]
        val primaryVal = when (primaryRaw) {
            is Boolean -> primaryRaw
            is String -> primaryRaw.equals("true", ignoreCase = true) || primaryRaw == "1"
            is Number -> primaryRaw.toInt() == 1
            else -> null
        }

        val backupRaw = backupPrefs.all[key]
        val backupVal = when (backupRaw) {
            is Boolean -> backupRaw
            is String -> backupRaw.equals("true", ignoreCase = true) || backupRaw == "1"
            is Number -> backupRaw.toInt() == 1
            else -> null
        }

        return when {
            primaryVal == true -> {
                if (backupVal != true) {
                    backupPrefs.edit().putString(key, "true").apply()
                }
                true
            }
            backupVal == true -> {
                Log.i(TAG, "Restored durable flag '$key' from setup backup store")
                primaryPrefs.edit().putString(key, "true").apply()
                true
            }
            else -> false
        }
    }

    suspend fun writeDurableFlag(key: String, value: Boolean) {
        val stringVal = if (value) "true" else "false"
        val (primaryOk, backupOk) = withContext(Dispatchers.IO) {
            val primaryResult = primaryPrefs.edit().putString(key, stringVal).commit()
            val backupResult = backupPrefs.edit().putString(key, stringVal).commit()
            primaryResult to backupResult
        }
        if (!primaryOk && !backupOk) {
            Log.e(TAG, "Failed to commit durable flag '$key' to storage")
        }
    }

    fun isPrivacyAccepted(): Boolean = readDurableFlag(KEY_PRIVACY_ACCEPTED)

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        writeDurableFlag(KEY_PRIVACY_ACCEPTED, accepted)
    }

    fun isOnboardingComplete(): Boolean = readDurableFlag(KEY_ONBOARDING_COMPLETE)

    suspend fun setOnboardingComplete(completed: Boolean) {
        writeDurableFlag(KEY_ONBOARDING_COMPLETE, completed)
    }

    fun isUserConsentedBackgroundService(): Boolean = readDurableFlag(KEY_USER_CONSENTED_BACKGROUND_SERVICE)

    suspend fun setUserConsentedBackgroundService(consented: Boolean) {
        writeDurableFlag(KEY_USER_CONSENTED_BACKGROUND_SERVICE, consented)
    }

    fun getProtectionMode(): String {
        val primary = primaryPrefs.all[KEY_PROTECTION_MODE] as? String
        val backup = backupPrefs.all[KEY_PROTECTION_MODE] as? String
        val mode = primary ?: backup ?: "standard"
        if (primary == null && backup != null) {
            primaryPrefs.edit().putString(KEY_PROTECTION_MODE, mode).apply()
        } else if (primary != null && backup == null) {
            backupPrefs.edit().putString(KEY_PROTECTION_MODE, mode).apply()
        }
        return mode
    }

    suspend fun setProtectionMode(mode: String) {
        withContext(Dispatchers.IO) {
            primaryPrefs.edit().putString(KEY_PROTECTION_MODE, mode).commit()
            backupPrefs.edit().putString(KEY_PROTECTION_MODE, mode).commit()
        }
    }
}
