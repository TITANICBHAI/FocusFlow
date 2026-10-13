package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Durable daily/weekly report notes.
 *
 * The composite key matches the legacy RN table: one note per reference date
 * and report type (`day` or `week`).
 */
@Entity(
    tableName = "report_notes",
    primaryKeys = ["ref_date", "type"],
)
data class ReportNoteEntity(
    @ColumnInfo(name = "ref_date")
    val refDate: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
)