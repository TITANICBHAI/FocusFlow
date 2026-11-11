package com.tbtechs.focusflow

/**
 * Decides whether a file another app opened with FocusFlow is a backup to stage for import.
 *
 * The manifest filter (two aliases of MainActivity, see AndroidManifest.xml) only narrows by file name on a
 * best-effort basis, and some file managers hand over URIs with no name in the path. So MainActivity reads
 * the file's real display name and this policy accepts only names that end in `.focusflow`.
 */
internal object ExternalBackupIntentPolicy {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val BACKUP_EXTENSION = ".focusflow"

    /** A file (content or file URI) being opened with VIEW. Web links and other actions are not our business here. */
    fun isFileView(action: String?, uriScheme: String?): Boolean =
        action == ACTION_VIEW &&
            (uriScheme.equals("content", ignoreCase = true) || uriScheme.equals("file", ignoreCase = true))

    fun hasBackupExtension(fileName: String?): Boolean =
        fileName?.trim()?.endsWith(BACKUP_EXTENSION, ignoreCase = true) == true

    fun shouldStage(action: String?, uriScheme: String?, fileName: String?): Boolean =
        isFileView(action, uriScheme) && hasBackupExtension(fileName)
}
