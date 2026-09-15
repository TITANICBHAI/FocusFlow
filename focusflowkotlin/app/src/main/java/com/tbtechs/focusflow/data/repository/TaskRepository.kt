package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.dao.TasksByHourRow
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import com.tbtechs.focusflow.data.model.Reminder
import com.tbtechs.focusflow.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * TaskRepository
 *
 * The single point of access for task persistence. Wraps [TaskDao], handles
 * all JSON serialization for [Task.tags], [Task.reminders], and
 * [Task.focusAllowedPackages], and maps between [TaskEntity] (Room layer) and
 * [Task] (domain layer).
 *
 * Called by: `TaskViewModel` — do not add non-task concerns here.
 *
 * **focusAllowedPackages semantics** (enforced throughout):
 *   domain null       ↔  entity null       = "use global setting"
 *   domain emptyList  ↔  entity `"[]"`     = "all apps allowed"
 *   domain listOf(…)  ↔  entity `"[\"…\"]"` = specific allow-list
 * null and emptyList() are NEVER silently collapsed.
 *
 * Constructor injection keeps the repository unit-testable with a fake DAO.
 * The database singleton is built in `di/AppModule.kt` and the DAO is
 * retrieved via `database.taskDao()` before passing to this constructor.
 */
class TaskRepository(private val taskDao: TaskDao) {

    // Lenient parser: mirrors `safeJsonParse` in database.ts — ignores unknown
    // fields so tasks written by a newer app version survive a downgrade read.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Public API (called by TaskViewModel) ─────────────────────────────────

    /**
     * Reactive stream of all tasks ordered by start time.
     * Room re-emits on every write; `TaskViewModel` collects this as a
     * `StateFlow<List<Task>>` — no manual `refresh()` calls needed.
     * Maps to `dbGetAllTasks`.
     */
    fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAllTasks().map { entities -> entities.map { it.toDomain() } }

    /** One-shot task snapshot used by the native backup coordinator. */
    suspend fun getAllTasks(): List<Task> = observeAllTasks().first()

    /**
     * Keeps the backup envelope field names aligned with the serialized domain
     * model used by the existing React Native backup format.
     */
    fun taskToBackupJson(task: Task): JSONObject =
        JSONObject(json.encodeToString(task))

    /** Parses one backup task without dropping unknown future fields. */
    fun taskFromBackupJson(raw: JSONObject): Task? =
        runCatching { json.decodeFromString<Task>(raw.toString()) }.getOrNull()

    /**
     * Tasks that ended within the last 24 h but are still unresolved.
     * Used by the startup sequence to surface yesterday's incomplete tasks.
     * Maps to `dbGetRecentUnresolvedTasks`.
     */
    suspend fun getRecentUnresolvedTasks(): List<Task> {
        val now = Instant.now()
        val cutoff = now.minusMillis(24L * 60 * 60 * 1_000).toString()
        return taskDao.getRecentUnresolvedTasks(cutoff = cutoff, now = now.toString())
            .map { it.toDomain() }
    }

    /**
     * Tasks whose local-calendar date falls in [[startISO], [endISO]] inclusive.
     * Both arguments are ISO-8601 timestamps; this function converts them to
     * local "YYYY-MM-DD" strings before the query (matching `dbGetTasksInDateRange`).
     * Maps to `dbGetTasksInDateRange`.
     */
    suspend fun getTasksInDateRange(startISO: String, endISO: String): List<Task> {
        val start = isoToLocalDate(startISO)
        val end   = isoToLocalDate(endISO)
        return taskDao.getTasksInDateRange(start = start, end = end).map { it.toDomain() }
    }

    /**
     * Tasks scheduled on the local-calendar day that [dateISO] falls in.
     * Maps to `dbGetTasksForDate`.
     */
    suspend fun getTasksForDate(dateISO: String): List<Task> {
        val localDate = isoToLocalDate(dateISO)
        return taskDao.getTasksForDate(localDate).map { it.toDomain() }
    }

    /**
     * Inserts [task]; silently ignores duplicate IDs (INSERT OR IGNORE).
     * Maps to `dbInsertTask`.
     */
    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.toEntity())
    }

    /**
     * Updates an existing task row by primary key.
     * Maps to `dbUpdateTask`.
     */
    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.toEntity())
    }

    /**
     * Atomically updates multiple tasks in a single SQLite transaction.
     * Room wraps `@Update(List)` in a transaction automatically.
     * Maps to `dbUpdateTasksBatch`.
     */
    suspend fun updateTasksBatch(tasks: List<Task>) {
        taskDao.updateTasks(tasks.map { it.toEntity() })
    }

    /** Deletes the task with [taskId]. Maps to `dbDeleteTask`. */
    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }

    /** Deletes the task table in one Room operation after callers clear active focus. */
    suspend fun deleteAllTasks() {
        taskDao.deleteAllTasks()
    }

    /**
     * Counts tasks (and their completed subset) by local hour-of-day within
     * [[startISO], [endISO]). Forwarded from [TaskDao.getTasksByHourOfDay].
     * Called by the analytics engine / future StatsRepository.
     */
    suspend fun getTasksByHourOfDay(startISO: String, endISO: String): List<TasksByHourRow> =
        taskDao.getTasksByHourOfDay(startISO, endISO)

    // ─── Entity ↔ Domain mappers (private) ───────────────────────────────────

    /**
     * Maps a [TaskEntity] (Room row) to the [Task] domain object.
     *
     * JSON fields are parsed with [json] using the same lenient / safe-parse
     * semantics as `safeJsonParse` in `database.ts`: a malformed field falls
     * back to an empty collection rather than throwing and wiping the task list.
     *
     * [focusAllowedPackages] nullability is preserved exactly:
     *   entity null  → domain null   (= "use global setting")
     *   entity `"[]"` → domain emptyList() (= "all apps allowed")
     */
    private fun TaskEntity.toDomain(): Task = Task(
        id                  = id,
        title               = title,
        description         = description,
        startTime           = startTime,
        endTime             = endTime,
        durationMinutes     = durationMinutes,
        status              = status,
        priority            = priority,
        tags                = safeDecodeList(tags),
        reminders           = safeDecodeList<Reminder>(reminders),
        color               = color,
        focusMode           = focusMode,
        focusAllowedPackages = focusAllowedPackages?.let { safeDecodeList(it) },
        createdAt           = createdAt,
        updatedAt           = updatedAt,
    )

    /**
     * Maps a [Task] domain object to [TaskEntity] for Room writes.
     *
     * [focusAllowedPackages] mapping:
     *   domain null      → entity null      (INSERT NULL — "use global setting")
     *   domain emptyList → entity `"[]"`    (INSERT "[]" — "all apps allowed")
     *   domain list      → entity JSON str
     */
    private fun Task.toEntity(): TaskEntity = TaskEntity(
        id                   = id,
        title                = title,
        description          = description,
        startTime            = startTime,
        endTime              = endTime,
        durationMinutes      = durationMinutes,
        status               = status,
        priority             = priority,
        tags                 = json.encodeToString(tags),
        reminders            = json.encodeToString(reminders),
        color                = color,
        focusMode            = focusMode,
        focusAllowedPackages = focusAllowedPackages?.let { json.encodeToString(it) },
        createdAt            = createdAt,
        updatedAt            = updatedAt,
    )

    // ─── Utilities ────────────────────────────────────────────────────────────

    /**
     * Converts an ISO-8601 timestamp to a local "YYYY-MM-DD" date string in
     * the device's default timezone. Mirrors `localDateString()` / the inline
     * local-date conversion in `database.ts` query helpers.
     */
    private fun isoToLocalDate(iso: String): String =
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /**
     * JSON-decodes a list, returning an empty list on any parse error.
     * Mirrors `safeJsonParse<T[]>(raw, [])` from `database.ts`.
     */
    private inline fun <reified T> safeDecodeList(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
}
