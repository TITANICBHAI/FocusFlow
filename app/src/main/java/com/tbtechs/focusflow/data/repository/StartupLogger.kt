package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.tbtechs.focusflow.BuildConfig
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Serializable
data class StartupLogEntry(
    val timestamp: String,
    val level: StartupLogLevel,
    val tag: String,
    val message: String,
)

@Serializable
enum class StartupLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Persistent, bounded application diagnostics.
 *
 * Product settings stay in SettingsRepository and domain data stays in Room.
 * This store is only for startup and operational diagnostics, matching the
 * reference app's persistent startup log without introducing another settings
 * database.
 */
object StartupLogger {
    private const val TAG = "StartupLogger"
    private const val PREFS_NAME = "focusflow_diagnostics"
    private const val LOG_KEY = "focusflow_startup_log"
    private const val LOG_FILE_NAME = "focusflow-boot.log"
    private const val MAX_ENTRIES = 500
    private const val DEFAULT_RECENT_LIMIT = 100

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()
    private val sessionId = "boot-${System.currentTimeMillis().toString(36)}"

    private var appContext: Context? = null
    private var persistenceExecutor: ExecutorService? = null
    private var initialized = false
    private var bootMarkerWritten = false
    private var warmResumeMarkerWritten = false
    private var hadPreviousHistory = false
    private val history = mutableListOf<StartupLogEntry>()

    /**
     * Loads the existing snapshot once and prepares the serialized persistence
     * queue. A corrupt snapshot is treated as empty diagnostic history.
     */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (!initialized) {
                appContext = context.applicationContext
                loadExistingHistoryLocked()
                hadPreviousHistory = history.isNotEmpty()
                persistenceExecutor = Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "focusflow-startup-logger").apply {
                        isDaemon = true
                    }
                }
                initialized = true
                return
            }

            if (bootMarkerWritten && !warmResumeMarkerWritten) {
                warmResumeMarkerWritten = true
                appendLocked(
                    level = StartupLogLevel.INFO,
                    tag = "StartupLogger",
                    message = "[WARM_RESUME $sessionId] App reinitialized within same process",
                    notifyLiveError = false,
                )
            }
        }
    }

    fun debug(tag: String, message: String) {
        record(StartupLogLevel.DEBUG, tag, message)
    }

    fun info(tag: String, message: String) {
        record(StartupLogLevel.INFO, tag, message)
    }

    fun warn(tag: String, message: String) {
        record(StartupLogLevel.WARN, tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        record(StartupLogLevel.ERROR, tag, message, throwable)
    }

    fun recent(limit: Int = DEFAULT_RECENT_LIMIT): List<StartupLogEntry> {
        synchronized(lock) {
            return history.takeLast(limit.coerceAtLeast(0))
        }
    }

    fun all(): List<StartupLogEntry> = synchronized(lock) {
        history.toList()
    }

    fun clear() {
        synchronized(lock) {
            history.clear()
            hadPreviousHistory = false
            bootMarkerWritten = false
            warmResumeMarkerWritten = false
            val context = appContext ?: return
            persistenceExecutor?.execute {
                runCatching {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(LOG_KEY)
                        .commit()
                    logFile(context).delete()
                }.onFailure { error ->
                    Log.w(TAG, "Unable to clear persistent startup diagnostics", error)
                }
            }
        }
    }

    fun formatForShare(): String {
        val formatted = synchronized(lock) {
            history.joinToString("\n", transform = ::formatEntry)
        }
        return sanitize(formatted)
    }

    fun bootSessionId(): String = sessionId

    private fun record(
        level: StartupLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val normalizedTag = tag.trim().ifBlank { "App" }
        val normalizedMessage = message.trim().ifBlank { "(no message)" }

        runCatching {
            Log.println(
                when (level) {
                    StartupLogLevel.DEBUG -> Log.DEBUG
                    StartupLogLevel.INFO -> Log.INFO
                    StartupLogLevel.WARN -> Log.WARN
                    StartupLogLevel.ERROR -> Log.ERROR
                },
                TAG,
                "[$normalizedTag] $normalizedMessage",
            )
            if (level == StartupLogLevel.ERROR && throwable != null) {
                Log.e(TAG, "[$normalizedTag] diagnostic exception", throwable)
            }
        }

        synchronized(lock) {
            appendLocked(
                level = level,
                tag = normalizedTag,
                message = normalizedMessage,
                notifyLiveError = level == StartupLogLevel.ERROR,
            )
        }
    }

    private fun appendLocked(
        level: StartupLogLevel,
        tag: String,
        message: String,
        notifyLiveError: Boolean,
    ) {
        if (!bootMarkerWritten) {
            bootMarkerWritten = true
            history += StartupLogEntry(
                timestamp = nowTimestamp(),
                level = StartupLogLevel.INFO,
                tag = "StartupLogger",
                message = if (hadPreviousHistory) {
                    "[NEW_PROCESS $sessionId] App relaunched after kill"
                } else {
                    "[COLD_START $sessionId] First session since install or log clear"
                },
            )
        }

        history += StartupLogEntry(
            timestamp = nowTimestamp(),
            level = level,
            tag = tag,
            message = message,
        )
        if (history.size > MAX_ENTRIES) {
            history.subList(0, history.size - MAX_ENTRIES).clear()
        }

        // Persistence is serialized on one executor so a newer snapshot cannot
        // be overwritten by an older concurrent read-modify-write operation.
        enqueueSnapshotLocked()

        if (notifyLiveError) {
            // The logger owns durable diagnostics; AppErrorEvents remains the
            // process-local stream used by the immediate error banner.
            AppErrorEvents.reportFromStartupLogger(tag, message)
        }
    }

    private fun loadExistingHistoryLocked() {
        val context = appContext ?: return
        val raw = runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.all[LOG_KEY] as? String
        }.getOrNull() ?: return

        runCatching {
            val parsed = json.decodeFromString<List<StartupLogEntry>>(raw)
            history += parsed.takeLast(MAX_ENTRIES)
        }.onFailure {
            history.clear()
            Log.w(TAG, "Ignoring corrupt persisted startup diagnostics")
        }
    }

    private fun enqueueSnapshotLocked() {
        val context = appContext ?: return
        val snapshot = history
            .filter { BuildConfig.DEBUG || it.level == StartupLogLevel.WARN || it.level == StartupLogLevel.ERROR }
            .takeLast(MAX_ENTRIES)
        persistenceExecutor?.execute {
            persistSnapshot(context, snapshot)
        }
    }

    private fun persistSnapshot(context: Context, snapshot: List<StartupLogEntry>) {
        runCatching {
            val encoded = json.encodeToString(snapshot)
            val prefsSaved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(LOG_KEY, encoded)
                .commit()
            if (!prefsSaved) {
                Log.w(TAG, "SharedPreferences refused startup diagnostics snapshot")
            }

            val target = logFile(context)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(snapshot.joinToString("\n", transform = ::formatEntry))
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }.onFailure { error ->
            // Diagnostics must never crash or block core app behavior.
            Log.w(TAG, "Unable to persist startup diagnostics", error)
        }
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    private fun formatEntry(entry: StartupLogEntry): String =
        "${entry.timestamp} [${entry.level}][${entry.tag}] ${entry.message}"

    private fun nowTimestamp(): String = Instant.now().toString()

    private fun sanitize(value: String): String =
        value
            .replace(Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE), "[redacted-url]")
            .replace(Regex("""\bwww\.\S+""", RegexOption.IGNORE_CASE), "[redacted-url]")
            .replace(
                Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE),
                "[redacted-email]",
            )
            .replace(
                Regex("""\b(password|passwd|token|secret|api[_-]?key|pin)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE),
                "$1=[redacted]",
            )
}