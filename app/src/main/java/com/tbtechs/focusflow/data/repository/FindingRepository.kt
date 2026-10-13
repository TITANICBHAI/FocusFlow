package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.FindingAcknowledgementDao
import com.tbtechs.focusflow.data.local.dao.FindingDao
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

class FindingRepository(
    private val findingDao: FindingDao,
    private val ackDao: FindingAcknowledgementDao,
) {
    private companion object {
        private const val TAG = "FindingRepository"
        private const val SURFACE_COOLDOWN_DAYS = 7L
        private const val INTENTIONAL_DAYS = 60L
    }

    suspend fun getActiveFindings(): List<FindingEntity> =
        runCatching {
            findingDao.restorePreviouslySeenFindings()
            findingDao.getActiveFindings()
        }.onFailure { Log.e(TAG, "getActiveFindings failed", it) }
            .getOrDefault(emptyList())

    suspend fun submit(finding: FindingEntity): Boolean = runCatching {
        val existing = findingDao.getExisting(finding.detectionType, finding.subjectPackage)
        when {
            existing == null -> insertIfCooldownElapsed(finding)
            existing.state == "intentional" -> {
                val stillSuppressed = existing.suppressedUntil?.let { suppressedUntil ->
                    runCatching {
                        Instant.now().isBefore(Instant.parse(suppressedUntil))
                    }.getOrDefault(false)
                } == true
                if (stillSuppressed || existing.evidenceFingerprint == finding.evidenceFingerprint) {
                    false
                } else {
                    resurface(existing.id, finding)
                    true
                }
            }
            existing.state == "resolved" -> insertIfCooldownElapsed(finding)
            else -> {
                if (existing.evidenceFingerprint != finding.evidenceFingerprint) {
                    updateEvidenceInPlace(
                        id = existing.id,
                        state = stateAfterFingerprintChange(existing),
                        finding = finding,
                    )
                }
                false
            }
        }
    }.onFailure { Log.e(TAG, "submit failed", it) }.getOrDefault(false)

    suspend fun markSeen(id: String) {
        runCatching { findingDao.markSeen(id, now()) }
            .onFailure { Log.e(TAG, "markSeen($id) failed", it) }
    }

    suspend fun acknowledgeIntentional(id: String, evidenceFingerprint: String, note: String?) {
        runCatching {
            val timestamp = now()
            findingDao.setIntentional(
                id,
                Instant.now().plus(INTENTIONAL_DAYS, ChronoUnit.DAYS).toString(),
                timestamp,
            )
            ackDao.insert(
                FindingAcknowledgementEntity(
                    findingId = id,
                    response = "intentional",
                    evidenceFingerprint = evidenceFingerprint,
                    createdAt = timestamp,
                    note = note,
                ),
            )
        }.onFailure { Log.e(TAG, "acknowledgeIntentional($id) failed", it) }
    }

    suspend fun acknowledgeAware(id: String, evidenceFingerprint: String) {
        runCatching {
            val timestamp = now()
            findingDao.setAware(id, timestamp)
            ackDao.insert(
                FindingAcknowledgementEntity(
                    findingId = id,
                    response = "aware",
                    evidenceFingerprint = evidenceFingerprint,
                    createdAt = timestamp,
                    note = null,
                ),
            )
        }.onFailure { Log.e(TAG, "acknowledgeAware($id) failed", it) }
    }

    suspend fun markResolved(id: String) {
        runCatching { findingDao.markResolved(id, now()) }
            .onFailure { Log.e(TAG, "markResolved($id) failed", it) }
    }

    suspend fun pruneOldResolved(days: Long = 90L) {
        runCatching {
            findingDao.deleteOldResolved(Instant.now().minus(days, ChronoUnit.DAYS).toString())
        }.onFailure { Log.e(TAG, "pruneOldResolved failed", it) }
    }

    private suspend fun insertIfCooldownElapsed(finding: FindingEntity): Boolean {
        val recentFirstDetectedAt = findingDao.getMostRecentFirstDetectedAt()
        if (recentFirstDetectedAt != null &&
            ChronoUnit.DAYS.between(Instant.parse(recentFirstDetectedAt), Instant.now()) <
            SURFACE_COOLDOWN_DAYS
        ) {
            return false
        }
        findingDao.insert(finding)
        return true
    }

    private suspend fun resurface(id: String, finding: FindingEntity) {
        findingDao.resurface(
            id = id,
            fingerprint = finding.evidenceFingerprint,
            evidenceJson = finding.evidenceJson,
            headline = finding.headline,
            body = finding.body,
            evidenceLine = finding.evidenceLine,
            now = now(),
        )
    }

    private suspend fun updateEvidenceInPlace(
        id: String,
        state: String,
        finding: FindingEntity,
    ) {
        findingDao.updateEvidenceInPlace(
            id = id,
            state = state,
            fingerprint = finding.evidenceFingerprint,
            evidenceJson = finding.evidenceJson,
            headline = finding.headline,
            body = finding.body,
            evidenceLine = finding.evidenceLine,
            now = now(),
        )
    }

    private suspend fun stateAfterFingerprintChange(existing: FindingEntity): String {
        if (existing.state != "detected") return existing.state

        val latestResponse = ackDao.getForFinding(existing.id).firstOrNull()?.response
        return when {
            latestResponse == "intentional" -> "detected"
            latestResponse == "aware" -> "aware"
            existing.seenAt != null -> "seen"
            else -> "detected"
        }
    }

    private fun now(): String = Instant.now().toString()
}