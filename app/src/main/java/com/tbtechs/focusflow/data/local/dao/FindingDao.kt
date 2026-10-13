package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FindingEntity

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(finding: FindingEntity)

    @Query("SELECT * FROM findings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FindingEntity?

    @Query("""
        SELECT * FROM findings
        WHERE state NOT IN ('intentional', 'resolved')
        ORDER BY CASE state WHEN 'detected' THEN 0 WHEN 'seen' THEN 1 ELSE 2 END ASC,
                 first_detected_at DESC
    """)
    suspend fun getActiveFindings(): List<FindingEntity>

    @Query("""
        UPDATE findings SET state = CASE
            WHEN (
                SELECT response FROM finding_acknowledgements
                WHERE finding_id = findings.id
                ORDER BY created_at DESC, id DESC
                LIMIT 1
            ) = 'aware' THEN 'aware'
            ELSE 'seen'
        END
        WHERE state = 'detected'
          AND COALESCE((
              SELECT response FROM finding_acknowledgements
              WHERE finding_id = findings.id
              ORDER BY created_at DESC, id DESC
              LIMIT 1
          ), '') != 'intentional'
          AND (
              seen_at IS NOT NULL
              OR (
                  SELECT response FROM finding_acknowledgements
                  WHERE finding_id = findings.id
                  ORDER BY created_at DESC, id DESC
                  LIMIT 1
              ) = 'aware'
          )
    """)
    suspend fun restorePreviouslySeenFindings()

    @Query("SELECT * FROM findings WHERE state = 'detected' ORDER BY first_detected_at DESC LIMIT 1")
    suspend fun getMostRecentDetected(): FindingEntity?

    @Query("SELECT first_detected_at FROM findings ORDER BY first_detected_at DESC LIMIT 1")
    suspend fun getMostRecentFirstDetectedAt(): String?

    @Query("""
        SELECT * FROM findings
        WHERE detection_type = :detectionType
          AND (subject_package = :subjectPackage
               OR (:subjectPackage IS NULL AND subject_package IS NULL))
        LIMIT 1
    """)
    suspend fun getExisting(detectionType: String, subjectPackage: String?): FindingEntity?

    @Query("UPDATE findings SET state = 'seen', seen_at = :now, last_updated_at = :now WHERE id = :id AND state = 'detected'")
    suspend fun markSeen(id: String, now: String)

    @Query("UPDATE findings SET state = 'intentional', suppressed_until = :suppressedUntil, last_updated_at = :now WHERE id = :id")
    suspend fun setIntentional(id: String, suppressedUntil: String, now: String)

    @Query("UPDATE findings SET state = 'aware', last_updated_at = :now WHERE id = :id")
    suspend fun setAware(id: String, now: String)

    @Query("UPDATE findings SET state = 'resolved', resolved_at = :now, last_updated_at = :now WHERE id = :id")
    suspend fun markResolved(id: String, now: String)

    @Query("""
        UPDATE findings SET state = 'detected', suppressed_until = NULL,
        evidence_fingerprint = :fingerprint, evidence_json = :evidenceJson,
        headline = :headline, body = :body, evidence_line = :evidenceLine,
        last_updated_at = :now WHERE id = :id
    """)
    suspend fun resurface(
        id: String,
        fingerprint: String,
        evidenceJson: String,
        headline: String,
        body: String,
        evidenceLine: String,
        now: String,
    )

    @Query("""
        UPDATE findings SET state = :state,
        evidence_fingerprint = :fingerprint, evidence_json = :evidenceJson,
        headline = :headline, body = :body, evidence_line = :evidenceLine,
        last_updated_at = :now WHERE id = :id
    """)
    suspend fun updateEvidenceInPlace(
        id: String,
        state: String,
        fingerprint: String,
        evidenceJson: String,
        headline: String,
        body: String,
        evidenceLine: String,
        now: String,
    )

    @Query("DELETE FROM findings WHERE state = 'resolved' AND resolved_at < :cutoffIso")
    suspend fun deleteOldResolved(cutoffIso: String)
}