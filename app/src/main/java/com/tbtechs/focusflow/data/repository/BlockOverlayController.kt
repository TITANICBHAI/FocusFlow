package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * BlockOverlayController
 *
 * Converted from BlockOverlayModule.
 * Stores overlay configuration in the SharedPreferences namespace read directly
 * by BlockOverlayActivity when the enforcement path launches it.
 */
class BlockOverlayController(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "focusday_prefs"

        val DEFAULT_QUOTES = listOf(
            "The present moment is the only time over which we have dominion.",
            "Focus is the art of knowing what to ignore.",
            "Deep work is the superpower of the 21st century.",
            "Your future self is watching. Don't let them down.",
            "One task at a time. One step at a time. One breath at a time.",
            "Discipline is choosing between what you want now and what you want most.",
            "The successful warrior is the average person with laser-like focus.",
            "Where attention goes, energy flows.",
            "Distraction is the enemy of vision.",
            "Every time you resist the urge to check, you grow stronger.",
            "You don't need to check your phone. The world can wait.",
            "Protect your attention like you protect your money.",
            "Clarity comes from action, not thought.",
            "Small disciplines repeated with consistency lead to great achievements.",
            "The cost of distraction is the loss of the life you could have built.",
        )
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun setOverlayQuote(quote: String) {
        prefs.edit().putString("block_overlay_quote", quote.trim()).apply()
    }

    suspend fun setCustomQuotes(quotesJson: String) {
        try {
            val trimmed = quotesJson.trim()
            if (trimmed.isEmpty() || trimmed == "[]") {
                prefs.edit().remove("block_overlay_quotes").apply()
                return
            }

            val array = JSONArray(trimmed)
            if (array.length() == 0) {
                prefs.edit().remove("block_overlay_quotes").apply()
            } else {
                prefs.edit().putString("block_overlay_quotes", trimmed).apply()
            }
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "quotes must be a valid JSON array: ${error.message}",
                error,
            )
        }
    }

    suspend fun clearCustomQuote() {
        prefs.edit().remove("block_overlay_quote").apply()
    }

    suspend fun setOverlayWallpaper(absolutePath: String) {
        if (absolutePath.isBlank()) {
            throw IllegalArgumentException("Path cannot be empty")
        }
        val file = File(absolutePath)
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException(
                "File does not exist or is not readable: $absolutePath",
            )
        }
        prefs.edit().putString("block_overlay_wallpaper", absolutePath).apply()
    }

    suspend fun clearOverlayWallpaper() {
        prefs.edit().remove("block_overlay_wallpaper").apply()
    }

    suspend fun getDefaultQuotes(): String {
        val array = JSONArray()
        DEFAULT_QUOTES.forEach { array.put(it) }
        return array.toString()
    }

    suspend fun getOverlaySettings(): String =
        JSONObject().apply {
            put("quote", prefs.getString("block_overlay_quote", "") ?: "")
            put("quotesJson", prefs.getString("block_overlay_quotes", "") ?: "")
            put("wallpaperPath", prefs.getString("block_overlay_wallpaper", "") ?: "")
        }.toString()
}