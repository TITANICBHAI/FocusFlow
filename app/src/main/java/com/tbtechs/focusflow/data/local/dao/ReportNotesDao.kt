package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.ReportNoteEntity

@Dao
interface ReportNotesDao {

    @Query("""
        SELECT * FROM report_notes
        WHERE ref_date = :refDate AND type = :type
        LIMIT 1
    """)
    suspend fun getNote(refDate: String, type: String): ReportNoteEntity?

    @Query("""
        SELECT * FROM report_notes
        WHERE ref_date BETWEEN :startDate AND :endDate
          AND type = :type
        ORDER BY ref_date ASC
    """)
    suspend fun getNotes(
        startDate: String,
        endDate: String,
        type: String,
    ): List<ReportNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: ReportNoteEntity)

    @Query("DELETE FROM report_notes WHERE ref_date = :refDate AND type = :type")
    suspend fun delete(refDate: String, type: String)
}