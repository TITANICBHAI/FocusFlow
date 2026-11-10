package com.tbtechs.focusflow

internal object ExternalBackupIntentPolicy {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"

    fun shouldStage(
        action: String?,
        uriScheme: String?,
        mimeType: String?,
        lastPathSegment: String?,
        displayName: String? = null,
    ): Boolean {
        if (
            action != ACTION_VIEW ||
            !(uriScheme.equals("content", ignoreCase = true) ||
                uriScheme.equals("file", ignoreCase = true))
        ) {
            return false
        }

        // The real file name, when the provider gives one, settles it: a .focusflow file is
        // accepted and anything else is not, whatever the MIME type or the URI path look like.
        if (!displayName.isNullOrBlank()) {
            return displayName.trim().endsWith(".focusflow", ignoreCase = true)
        }

        val normalizedMimeType = mimeType?.substringBefore(';')?.trim()
        val isOctetStream = normalizedMimeType.equals(
            OCTET_STREAM_MIME_TYPE,
            ignoreCase = true,
        )
        val hasBackupExtension = lastPathSegment
            ?.endsWith(".focusflow", ignoreCase = true) == true
        return isOctetStream || hasBackupExtension
    }
}
