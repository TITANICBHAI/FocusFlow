package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.FindingAcknowledgementDao
import com.tbtechs.focusflow.data.local.dao.FindingDao
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FindingRepositoryTest {

    @Test
    fun changedFingerprintsQuietlyUpdateCurrentAndPreviouslySeenRecords() =
        runBlocking {
            val scenarios = listOf(
                Scenario("seen", "seen", Instant.EPOCH.toString(), null, "seen"),
                Scenario("aware", "aware", null, "aware", "aware"),
                Scenario("previously resurfaced seen", "detected", Instant.EPOCH.toString(), null, "seen"),
                Scenario("previously resurfaced aware", "detected", null, "aware", "aware"),
                Scenario("expired intentional acknowledgement", "detected", Instant.EPOCH.toString(), "intentional", "detected"),
            )

            scenarios.forEach { scenario ->
                var stored = finding(
                    id = "existing-${scenario.name}",
                    state = scenario.currentState,
                    fingerprint = "old-fingerprint",
                ).copy(seenAt = scenario.seenAt)
                var insertCalls = 0
                var resurfaceCalls = 0
                val updates = mutableListOf<Pair<String, String>>()
                val dao = fakeDao(FindingDao::class.java) { method, args ->
                    when (method.name) {
                        "getExisting" -> stored
                        "insert" -> {
                            insertCalls++
                            Unit
                        }
                        "resurface" -> {
                            resurfaceCalls++
                            Unit
                        }
                        "updateEvidenceInPlace" -> {
                            val id = args[0] as String
                            val state = args[1] as String
                            val fingerprint = args[2] as String
                            updates += id to fingerprint
                            stored = stored.copy(
                                state = state,
                                evidenceFingerprint = fingerprint,
                                evidenceJson = args[3] as String,
                                headline = args[4] as String,
                                body = args[5] as String,
                                evidenceLine = args[6] as String,
                                lastUpdatedAt = args[7] as String,
                            )
                            Unit
                        }
                        else -> null
                    }
                }
                val ackDao = fakeDao(FindingAcknowledgementDao::class.java) { method, _ ->
                    if (method.name == "getForFinding") {
                        scenario.latestResponse?.let { listOf(acknowledgement(stored.id, it)) }
                            ?: emptyList<FindingAcknowledgementEntity>()
                    } else {
                        Unit
                    }
                }
                val repository = FindingRepository(dao, ackDao)

                assertFalse(repository.submit(finding("candidate-1", "new-fingerprint-1")))
                assertEquals(scenario.expectedState, stored.state)
                assertEquals("new-fingerprint-1", stored.evidenceFingerprint)
                assertFalse(repository.submit(finding("candidate-2", "new-fingerprint-2")))
                assertEquals(scenario.expectedState, stored.state)
                assertEquals("new-fingerprint-2", stored.evidenceFingerprint)
                assertEquals(
                    listOf(
                        stored.id to "new-fingerprint-1",
                        stored.id to "new-fingerprint-2",
                    ),
                    updates,
                )
                assertEquals(0, insertCalls)
                assertEquals(0, resurfaceCalls)
            }
        }

    @Test
    fun activeFindingsRestorePreviouslySurfacedSeenOrAwareStateBeforeReading() = runBlocking {
        val calls = mutableListOf<String>()
        val existing = finding("previously-seen", "current-fingerprint", state = "seen")
        val dao = fakeDao(FindingDao::class.java) { method, _ ->
            when (method.name) {
                "restorePreviouslySeenFindings" -> {
                    calls += "restore"
                    Unit
                }
                "getActiveFindings" -> {
                    calls += "read"
                    listOf(existing)
                }
                else -> null
            }
        }
        val repository = FindingRepository(
            dao,
            fakeDao(FindingAcknowledgementDao::class.java) { _, _ -> Unit },
        )

        assertEquals(listOf(existing), repository.getActiveFindings())
        assertEquals(listOf("restore", "read"), calls)
    }

    @Test
    fun fingerprintChangesDoNotBreakActiveIntentionalSuppression() = runBlocking {
        var intentionalUpdateCalls = 0
        var intentionalInsertCalls = 0
        var intentionalResurfaceCalls = 0
        val intentional = finding("intentional", "old-fingerprint").copy(
            state = "intentional",
            suppressedUntil = Instant.now().plus(30, ChronoUnit.DAYS).toString(),
        )
        val intentionalDao = fakeDao(FindingDao::class.java) { method, _ ->
            when (method.name) {
                "getExisting" -> intentional
                "insert" -> {
                    intentionalInsertCalls++
                    Unit
                }
                "resurface" -> {
                    intentionalResurfaceCalls++
                    Unit
                }
                "updateEvidenceInPlace" -> {
                    intentionalUpdateCalls++
                    Unit
                }
                else -> null
            }
        }
        val intentionalRepository = FindingRepository(
            intentionalDao,
            fakeDao(FindingAcknowledgementDao::class.java) { _, _ -> Unit },
        )

        assertFalse(intentionalRepository.submit(finding("candidate", "new-fingerprint")))
        assertEquals(0, intentionalInsertCalls)
        assertEquals(0, intentionalResurfaceCalls)
        assertEquals(0, intentionalUpdateCalls)
    }

    private data class Scenario(
        val name: String,
        val currentState: String,
        val seenAt: String?,
        val latestResponse: String?,
        val expectedState: String,
    )

    private fun acknowledgement(findingId: String, response: String) =
        FindingAcknowledgementEntity(
            findingId = findingId,
            response = response,
            evidenceFingerprint = "previous-acknowledged-fingerprint",
            createdAt = Instant.EPOCH.toString(),
            note = null,
        )

    private fun finding(
        id: String,
        fingerprint: String,
        state: String = "detected",
    ) = FindingEntity(
        id = id,
        detectionType = "INFINITE_SESSION_DESIGN",
        subjectPackage = "com.example.video",
        subjectAppName = "Video",
        state = state,
        evidenceFingerprint = fingerprint,
        evidenceJson = "{}",
        headline = "Infinite session design",
        body = "Observed session pattern",
        evidenceLine = "Observed across 14 days",
        firstDetectedAt = Instant.EPOCH.toString(),
        lastUpdatedAt = Instant.EPOCH.toString(),
        seenAt = null,
        resolvedAt = null,
        suppressedUntil = null,
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> fakeDao(
        daoType: Class<T>,
        answer: (Method, Array<out Any?>) -> Any?,
    ): T = Proxy.newProxyInstance(
        daoType.classLoader,
        arrayOf(daoType),
    ) { _, method, arguments ->
        answer(method, arguments ?: emptyArray())
    } as T
}
