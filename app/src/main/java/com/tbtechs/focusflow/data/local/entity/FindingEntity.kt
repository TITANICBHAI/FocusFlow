package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stateful behavioural finding: detected → seen → acknowledged → resolved. */
@Entity(
    tableName = "findings",
    indices = [
        Index(value = ["state"], name = "idx_findings_state"),
        Index(value = ["subject_package"], name = "idx_findings_package"),
    ],
)
data class FindingEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "detection_type") val detectionType: String,
    @ColumnInfo(name = "subject_package") val subjectPackage: String?,
    @ColumnInfo(name = "subject_app_name") val subjectAppName: String?,
    @ColumnInfo(name = "state", defaultValue = "detected") val state: String,
    @ColumnInfo(name = "evidence_fingerprint") val evidenceFingerprint: String,
    @ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @ColumnInfo(name = "headline") val headline: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "evidence_line") val evidenceLine: String,
    @ColumnInfo(name = "first_detected_at") val firstDetectedAt: String,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: String,
    @ColumnInfo(name = "seen_at") val seenAt: String?,
    @ColumnInfo(name = "resolved_at") val resolvedAt: String?,
    @ColumnInfo(name = "suppressed_until") val suppressedUntil: String?,
)