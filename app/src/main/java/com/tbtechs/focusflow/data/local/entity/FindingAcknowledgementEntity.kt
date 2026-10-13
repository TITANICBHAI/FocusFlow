package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Records one user response to a finding. */
@Entity(
    tableName = "finding_acknowledgements",
    foreignKeys = [
        ForeignKey(
            entity = FindingEntity::class,
            parentColumns = ["id"],
            childColumns = ["finding_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["finding_id"], name = "idx_finding_ack_finding_id")],
)
data class FindingAcknowledgementEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "finding_id") val findingId: String,
    @ColumnInfo(name = "response") val response: String,
    @ColumnInfo(name = "evidence_fingerprint") val evidenceFingerprint: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "note") val note: String?,
)