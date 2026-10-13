package com.tbtechs.focusflow.data.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalTimestampTest {
    @Test
    fun formatsUtcTimestampsWithExactlyMillisecondPrecision() {
        val instant = Instant.parse("2026-10-03T12:34:56.789456Z")

        val formatted = CanonicalTimestamp.format(instant)

        assertEquals("2026-10-03T12:34:56.789Z", formatted)
        assertEquals(Instant.parse("2026-10-03T12:34:56.789Z"), CanonicalTimestamp.parse(formatted))
    }

    @Test
    fun normalizesEveryPersistedTaskTimestamp() {
        val task = Task(
            id = "timestamp-test",
            title = "Timestamp test",
            startTime = "2026-10-03T12:00:00.1Z",
            endTime = "2026-10-03T12:30:00.12Z",
            durationMinutes = 30,
            status = "scheduled",
            priority = "medium",
            color = "#000000",
            focusMode = false,
            createdAt = "2026-10-03T11:00:00Z",
            updatedAt = "2026-10-03T11:30:00.123456789Z",
        )

        val normalized = task.withCanonicalTimestamps()

        assertEquals("2026-10-03T12:00:00.100Z", normalized.startTime)
        assertEquals("2026-10-03T12:30:00.120Z", normalized.endTime)
        assertEquals("2026-10-03T11:00:00.000Z", normalized.createdAt)
        assertEquals("2026-10-03T11:30:00.123Z", normalized.updatedAt)
    }
}