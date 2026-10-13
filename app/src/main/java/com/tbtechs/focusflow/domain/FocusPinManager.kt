package com.tbtechs.focusflow.domain

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Compatibility manager for the Focus Session Password used by the migrated
 * foreground-service/settings contract.
 *
 * The React Native implementation stores SHA-256(UTF-8(password)) under the
 * legacy session_pin_hash key. Keep this adapter byte-for-byte compatible so a
 * migrated user is not locked out, and never expose or log the raw password.
 */
class FocusPinManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences("focusday_prefs", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean = !prefs.getString(PREF_HASH, null).isNullOrBlank()

    fun setPin(pin: String) {
        prefs.edit().putString(PREF_HASH, hash(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(PREF_HASH, null) ?: return true
        return MessageDigest.isEqual(
            stored.lowercase().toByteArray(Charsets.UTF_8),
            hash(pin).toByteArray(Charsets.UTF_8),
        )
    }

    fun clearPin(pin: String): Boolean {
        if (!verifyPin(pin) || !isPinSet()) return false
        clearPin()
        return true
    }

    /** Clears the configured focus PIN after the UI has already verified it. */
    fun clearPin() {
        prefs.edit().remove(PREF_HASH).apply()
    }

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val PREF_HASH = "session_pin_hash"
    }
}