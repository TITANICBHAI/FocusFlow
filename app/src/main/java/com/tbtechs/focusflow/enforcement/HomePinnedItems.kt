package com.tbtechs.focusflow.enforcement

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The icons a person has pinned to the launcher's home screen: apps, and shortcuts that other
 * apps hand over through Android's "Add to Home screen" (a website from Chrome, a chat from a
 * messenger, and so on).
 *
 * Kept free of Android so the rules can be tested: a fixed maximum, no duplicates, and a text
 * format that survives any characters in a name. The launcher stores the text in its
 * preferences; this file is the only place that knows how.
 */
object HomePinnedItems {
    /** Preference key the launcher and the pin-request screen share. */
    const val PREF_KEY = "launcher_home_pinned_items"

    /** Preference key for the grid size: 3, 4 or 5 columns, always as many rows as columns. */
    const val PREF_SIZE_KEY = "launcher_home_pinned_size"
    const val MIN_COLUMNS = 3
    const val MAX_COLUMNS = 5
    const val DEFAULT_COLUMNS = 4

    /** The most that can ever be kept: a 5 by 5 grid. */
    const val MAX_ITEMS = MAX_COLUMNS * MAX_COLUMNS

    /** 3, 4 or 5; anything else, or nothing saved, means the default of 4. */
    fun columnsFromPref(raw: String?): Int =
        raw?.toIntOrNull()?.takeIf { it in MIN_COLUMNS..MAX_COLUMNS } ?: DEFAULT_COLUMNS

    /** A grid is as tall as it is wide, so 3 columns hold 9, 4 hold 16 and 5 hold 25. */
    fun capacity(columns: Int): Int = columns * columns

    enum class Kind { APP, SHORTCUT }

    /** [id] is the shortcut's id for a shortcut, and empty for an app. */
    data class Entry(val kind: Kind, val packageName: String, val id: String, val label: String) {
        val key: String get() = "${kind.name}|$packageName|$id"
    }

    enum class Status { ADDED, DUPLICATE, FULL }

    class AddOutcome(val items: List<Entry>, val status: Status)

    fun add(current: List<Entry>, entry: Entry, max: Int = capacity(DEFAULT_COLUMNS)): AddOutcome = when {
        current.any { it.key == entry.key } -> AddOutcome(current, Status.DUPLICATE)
        current.size >= max -> AddOutcome(current, Status.FULL)
        else -> AddOutcome(current + entry, Status.ADDED)
    }

    fun remove(current: List<Entry>, key: String): List<Entry> = current.filterNot { it.key == key }

    fun removeApp(current: List<Entry>, packageName: String): List<Entry> =
        current.filterNot { it.kind == Kind.APP && it.packageName == packageName }

    fun contains(current: List<Entry>, kind: Kind, packageName: String, id: String): Boolean =
        current.any { it.kind == kind && it.packageName == packageName && it.id == id }

    /** One entry per line, fields separated by tabs and each one URL-encoded. */
    fun serialize(items: List<Entry>): String = items.joinToString("\n") { e ->
        listOf(e.kind.name, e.packageName, e.id, e.label).joinToString("\t") { enc(it) }
    }

    /** Skips anything malformed or repeated, so a damaged value can never break the home screen. */
    fun parse(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (line in raw.split('\n')) {
            val parts = line.split('\t')
            if (parts.size != 4) continue
            val kind = Kind.values().firstOrNull { it.name == dec(parts[0]) } ?: continue
            val pkg = dec(parts[1])
            if (pkg.isBlank()) continue
            val entry = Entry(kind, pkg, dec(parts[2]), dec(parts[3]))
            if (kind == Kind.SHORTCUT && entry.id.isBlank()) continue
            if (seen.add(entry.key)) out.add(entry)
            if (out.size >= MAX_ITEMS) break
        }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        ""
    }
}
