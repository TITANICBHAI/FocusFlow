package com.tbtechs.focusflow.enforcement

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The Android app widgets (a weather panel, a calendar, a music player...) placed on the
 * launcher's home screen. Android owns the widget itself; this only remembers which widget ids
 * the launcher is hosting, which widget each one is, and how tall the person wants it.
 *
 * Kept free of Android so the rules can be tested.
 */
object HomeWidgetEntries {
    /** Preference key the launcher and the pin-request screen share. */
    const val PREF_KEY = "launcher_home_widgets_items"

    /** Enough for a calm home screen. More than this and the home would just be a scroll of widgets. */
    const val MAX_WIDGETS = 6

    enum class Size(val key: String, val heightDp: Int, val label: String) {
        SMALL("s", 110, "Small"),
        MEDIUM("m", 180, "Medium"),
        LARGE("l", 260, "Large"),
    }

    /** [provider] is the widget's component name as text, e.g. "com.example/.ClockWidget". */
    data class Entry(val appWidgetId: Int, val provider: String, val size: Size = Size.MEDIUM)

    enum class Status { ADDED, DUPLICATE, FULL }

    class AddOutcome(val items: List<Entry>, val status: Status)

    fun add(current: List<Entry>, entry: Entry, max: Int = MAX_WIDGETS): AddOutcome = when {
        current.any { it.appWidgetId == entry.appWidgetId } -> AddOutcome(current, Status.DUPLICATE)
        current.size >= max -> AddOutcome(current, Status.FULL)
        else -> AddOutcome(current + entry, Status.ADDED)
    }

    fun remove(current: List<Entry>, appWidgetId: Int): List<Entry> =
        current.filterNot { it.appWidgetId == appWidgetId }

    fun setSize(current: List<Entry>, appWidgetId: Int, size: Size): List<Entry> =
        current.map { if (it.appWidgetId == appWidgetId) it.copy(size = size) else it }

    /** A widget is never drawn shorter than it says it needs, whatever size was chosen. */
    fun heightDp(size: Size, providerMinHeightDp: Int): Int = maxOf(size.heightDp, providerMinHeightDp)

    fun serialize(items: List<Entry>): String = items.joinToString("\n") { e ->
        listOf(e.appWidgetId.toString(), URLEncoder.encode(e.provider, "UTF-8"), e.size.key).joinToString("\t")
    }

    /** Skips anything malformed or repeated, so a damaged value can never break the home screen. */
    fun parse(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<Int>()
        val out = ArrayList<Entry>()
        for (line in raw.split('\n')) {
            val parts = line.split('\t')
            if (parts.size != 3) continue
            val id = parts[0].toIntOrNull() ?: continue
            val provider = try {
                URLDecoder.decode(parts[1], "UTF-8")
            } catch (_: Exception) {
                continue
            }
            if (provider.isBlank()) continue
            val size = Size.values().firstOrNull { it.key == parts[2] } ?: Size.MEDIUM
            if (seen.add(id)) out.add(Entry(id, provider, size))
            if (out.size >= MAX_WIDGETS) break
        }
        return out
    }
}
