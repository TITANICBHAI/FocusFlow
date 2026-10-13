package com.tbtechs.focusflow.data.repository

import android.net.Uri
import java.nio.charset.StandardCharsets

/**
 * Stable, collision-resistant identity shared by every task-end PendingIntent
 * and its notification. Task IDs are URI path segments, not request-code hashes.
 */
object TaskEndAlarmIdentity {
    const val REQUEST_CODE = 0
    const val NOTIFICATION_ID = 9101
    private const val URI_PREFIX = "focusflow-internal://task-end/"
    private const val PATH_SEGMENT_SAFE = "-_.!~*'()"
    private const val HEX = "0123456789ABCDEF"

    fun dataUri(taskId: String): Uri = Uri.parse(dataUriString(taskId))

    fun dataUriString(taskId: String): String =
        "$URI_PREFIX${encodePathSegment(taskId)}"

    fun notificationTag(taskId: String): String = "task-end:$taskId"

    internal fun encodePathSegment(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val char = byte.toInt() and 0xff
                if (
                    char in 'a'.code..'z'.code ||
                    char in 'A'.code..'Z'.code ||
                    char in '0'.code..'9'.code ||
                    char.toChar() in PATH_SEGMENT_SAFE
                ) {
                    append(char.toChar())
                } else {
                    append('%')
                    append(HEX[char ushr 4])
                    append(HEX[char and 0x0f])
                }
            }
        }
    }
}