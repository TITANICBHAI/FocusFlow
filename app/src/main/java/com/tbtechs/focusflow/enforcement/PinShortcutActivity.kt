package com.tbtechs.focusflow.enforcement

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import com.tbtechs.focusflow.enforcement.HomePinnedItems.Entry
import com.tbtechs.focusflow.enforcement.HomePinnedItems.Kind
import com.tbtechs.focusflow.enforcement.HomePinnedItems.Status

/**
 * Receives "Add to Home screen" from other apps: a website from Chrome, a chat from a messenger.
 * Android hands the request to the default launcher through this screen.
 *
 * Nothing is added until the person taps Add, and the request is only accepted after that, so
 * an app cannot put icons on the home screen behind their back.
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = try {
            launcherApps?.getPinItemRequest(intent)
        } catch (_: Exception) {
            null
        }
        if (request != null && request.isValid &&
            request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET
        ) {
            askToAddWidget(request)
            return
        }
        val info = request
            ?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && it.isValid }
            ?.shortcutInfo
        if (request == null || info == null) {
            finish()
            return
        }
        val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty().ifBlank { "Shortcut" }
        val owner = info.`package`
        val ownerLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(owner, 0)).toString()
        } catch (_: Exception) {
            owner
        }
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle("Add to home?")
            .setMessage("\"$label\" from $ownerLabel will appear on your FocusFlow home screen.")
            .setPositiveButton("Add") { _, _ ->
                val current = HomePinnedItems.parse(prefs.getString(HomePinnedItems.PREF_KEY, null))
                val capacity = HomePinnedItems.capacity(
                    HomePinnedItems.columnsFromPref(prefs.getString(HomePinnedItems.PREF_SIZE_KEY, null)),
                )
                val outcome = HomePinnedItems.add(current, Entry(Kind.SHORTCUT, owner, info.id, label), capacity)
                when (outcome.status) {
                    Status.ADDED -> {
                        prefs.edit()
                            .putString(HomePinnedItems.PREF_KEY, HomePinnedItems.serialize(outcome.items))
                            .apply()
                        request.accept()
                        Toast.makeText(this, "Added to home", Toast.LENGTH_SHORT).show()
                    }
                    Status.DUPLICATE -> {
                        request.accept()
                        Toast.makeText(this, "Already on your home screen", Toast.LENGTH_SHORT).show()
                    }
                    Status.FULL ->
                        Toast.makeText(this, "Home is full. Remove one first.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    /** A widget offered through "Add to Home screen". Same rule: nothing happens until Add is tapped. */
    private fun askToAddWidget(request: LauncherApps.PinItemRequest) {
        val provider = try {
            request.getAppWidgetProviderInfo(this)
        } catch (_: Exception) {
            null
        }
        if (provider == null) {
            finish()
            return
        }
        val label = try { provider.loadLabel(packageManager) } catch (_: Exception) { "Widget" }
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        AlertDialog.Builder(this)
            .setTitle("Add widget to home?")
            .setMessage("\"$label\" will appear on your FocusFlow home screen.")
            .setPositiveButton("Add") { _, _ ->
                val current = HomeWidgetEntries.parse(prefs.getString(HomeWidgetEntries.PREF_KEY, null))
                if (current.size >= HomeWidgetEntries.MAX_WIDGETS) {
                    Toast.makeText(this, "Home is full. Remove a widget first.", Toast.LENGTH_LONG).show()
                } else {
                    val host = HomeWidgetHost.create(this)
                    val id = try { host.allocateAppWidgetId() } catch (_: Exception) { -1 }
                    val accepted = id >= 0 && try {
                        request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
                    } catch (_: Exception) {
                        false
                    }
                    if (accepted) {
                        val outcome = HomeWidgetEntries.add(
                            current,
                            HomeWidgetEntries.Entry(id, provider.provider.flattenToString()),
                        )
                        prefs.edit()
                            .putString(HomeWidgetEntries.PREF_KEY, HomeWidgetEntries.serialize(outcome.items))
                            .apply()
                        Toast.makeText(this, "Widget added to home", Toast.LENGTH_SHORT).show()
                    } else {
                        if (id >= 0) try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
                        Toast.makeText(this, "Couldn't add that widget.", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
