package com.tbtechs.focusflow.data.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Shared UTC millisecond timestamp format used for every persisted task time.
 */
object CanonicalTimestamp {
    private val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = formatter.format(instant)

    fun parse(value: String): Instant = Instant.parse(value)
}

/**
 * Normalizes every task timestamp at the persistence boundary, including
 * timestamps read from older exports that have variable fractional precision.
 */
fun Task.withCanonicalTimestamps(): Task = copy(
    startTime = CanonicalTimestamp.format(CanonicalTimestamp.parse(startTime)),
    endTime = CanonicalTimestamp.format(CanonicalTimestamp.parse(endTime)),
    createdAt = CanonicalTimestamp.format(CanonicalTimestamp.parse(createdAt)),
    updatedAt = CanonicalTimestamp.format(CanonicalTimestamp.parse(updatedAt)),
)