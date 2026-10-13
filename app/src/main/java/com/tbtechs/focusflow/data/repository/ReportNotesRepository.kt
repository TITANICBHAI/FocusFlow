package com.tbtechs.focusflow.data.repository

import android.content.Context
import com.tbtechs.focusflow.data.local.dao.ReportNotesDao
import com.tbtechs.focusflow.data.local.entity.ReportNoteEntity
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import java.time.Instant
import java.time.LocalDate

/**
 * Repository boundary for report history.
 *
 * Room is the source of truth for new reads and writes. The legacy preference
 * lookup is read-only and exists only to make a partial upgrade non-destructive
 * until the corresponding Room row is saved.
 */
class ReportNotesRepository(
    context: Context,
    private val dao: ReportNotesDao,
) {
    private val legacyStores = listOf(
        context.applicationContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
        context.applicationContext.getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE),
    )

    suspend fun getNote(refDate: LocalDate, type: String): String {
        val normalizedType = normalizeType(type)
        val persisted = dao.getNote(refDate.toString(), normalizedType)?.note
        if (persisted != null) return persisted

        val legacyKey = "report_note_${normalizedType}_${refDate.toString()}"
        return legacyStores.asSequence()
            .mapNotNull { it.all[legacyKey] as? String }
            .firstOrNull()
            .orEmpty()
    }

    suspend fun getDailyNotes(start: LocalDate, end: LocalDate): Map<String, String> =
        dao.getNotes(start.toString(), end.toString(), TYPE_DAY)
            .associate { it.refDate to it.note }
            .ifEmpty {
                legacyDailyNotes(start, end)
            }

    suspend fun saveNote(refDate: LocalDate, type: String, note: String) {
        val normalizedType = normalizeType(type)
        val trimmed = note.trim().take(MAX_NOTE_LENGTH)
        if (trimmed.isBlank()) {
            dao.delete(refDate.toString(), normalizedType)
        } else {
            dao.upsert(
                ReportNoteEntity(
                    refDate = refDate.toString(),
                    type = normalizedType,
                    note = trimmed,
                    updatedAt = Instant.now().toString(),
                ),
            )
        }
        // Remove only the obsolete compatibility copy. New note data never
        // goes to SharedPreferences.
        val legacyKey = "report_note_${normalizedType}_${refDate.toString()}"
        legacyStores.forEach { it.edit().remove(legacyKey).apply() }
    }

    private fun legacyDailyNotes(start: LocalDate, end: LocalDate): Map<String, String> {
        val notes = linkedMapOf<String, String>()
        var date = start
        while (!date.isAfter(end)) {
            val note = legacyStores.asSequence()
                .mapNotNull { it.all["report_note_day_$date"] as? String }
                .firstOrNull()
                ?.takeIf(String::isNotBlank)
            if (note != null) notes[date.toString()] = note
            date = date.plusDays(1)
        }
        return notes
    }

    private fun normalizeType(type: String): String =
        when (type.lowercase()) {
            TYPE_DAY -> TYPE_DAY
            TYPE_WEEK -> TYPE_WEEK
            else -> error("Unsupported report note type: $type")
        }

    companion object {
        const val TYPE_DAY = "day"
        const val TYPE_WEEK = "week"
        private const val MAX_NOTE_LENGTH = 4_000
    }
}