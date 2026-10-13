package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An installed application visible to FocusFlow's app-selection surfaces.
 *
 * [icon] is the PackageManager Drawable directly. The old bridge converted it
 * to PNG/base64 only because React Native could not receive Android Drawables.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isIme: Boolean,
    val icon: Drawable?,
    val isInstalled: Boolean = true,
)

fun missingInstalledAppInfo(
    packageName: String,
    label: String = "App not installed",
): InstalledAppInfo =
    InstalledAppInfo(
        packageName = packageName,
        appName = label,
        isIme = false,
        icon = null,
        isInstalled = false,
    )

/**
 * InstalledAppsRepository
 *
 * Returns launcher-visible applications and registered input methods, excluding
 * FocusFlow itself. IMEs remain included even when they have no launcher icon.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getInstalledApps(
        onAppLoaded: (suspend (InstalledAppInfo) -> Unit)? = null,
    ): List<InstalledAppInfo> {
        var servedFromCache = false
        val apps = cacheMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val cached = cachedCatalog
                ?.takeIf {
                    it.packageName == appContext.packageName &&
                        now - it.createdAtElapsedMs < CACHE_TTL_MS
                }
            if (cached != null) {
                servedFromCache = true
                cached.apps
            } else {
                loadInstalledApps(onAppLoaded).also { loaded ->
                    cachedCatalog = CachedCatalog(
                        packageName = appContext.packageName,
                        createdAtElapsedMs = SystemClock.elapsedRealtime(),
                        apps = loaded,
                    )
                }
            }
        }
        if (servedFromCache) apps.forEach { onAppLoaded?.invoke(it) }
        return apps
    }

    /** Forces the next request to refresh package metadata from Android. */
    fun invalidateCache() {
        cachedCatalog = null
    }

    private suspend fun loadInstalledApps(
        onAppLoaded: (suspend (InstalledAppInfo) -> Unit)?,
    ): List<InstalledAppInfo> {
        val packageManager = appContext.packageManager
        val imePackages = try {
            val inputMethodManager = appContext.getSystemService(
                Context.INPUT_METHOD_SERVICE,
            ) as? InputMethodManager
            inputMethodManager
                ?.inputMethodList
                ?.mapTo(mutableSetOf()) { it.packageName }
                ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }

        @Suppress("DEPRECATION")
        val applications = packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA,
        )

        val result = buildList {
            for (application in applications) {
                if (application.packageName == appContext.packageName) continue

                val isIme = application.packageName in imePackages
                val launchIntent = packageManager.getLaunchIntentForPackage(
                    application.packageName,
                )
                if (launchIntent == null && !isIme) continue

                val appName = try {
                    packageManager.getApplicationLabel(application).toString()
                } catch (_: Exception) {
                    "App name unavailable"
                }

                val icon = try {
                    packageManager.getApplicationIcon(application.packageName)
                } catch (_: Exception) {
                    null
                }

                val app = InstalledAppInfo(
                    packageName = application.packageName,
                    appName = appName,
                    isIme = isIme,
                    icon = icon,
                )
                add(app)
                onAppLoaded?.invoke(app)
            }
        }
        return result.sortedBy { it.appName.lowercase() }
    }

    /**
     * Loads the same app catalog while publishing small batches for progressive UI updates.
     *
     * The emitted batches contain only app metadata. Feature-specific selections and settings
     * remain owned by the caller.
     */
    suspend fun getInstalledAppsBatched(
        batchSize: Int = 24,
        onBatchLoaded: suspend (List<InstalledAppInfo>) -> Unit,
    ): List<InstalledAppInfo> {
        require(batchSize > 0) { "batchSize must be greater than zero" }

        val pending = ArrayList<InstalledAppInfo>(batchSize)
        val result = getInstalledApps { app ->
            pending += app
            if (pending.size >= batchSize) {
                onBatchLoaded(pending.toList())
                pending.clear()
            }
        }
        if (pending.isNotEmpty()) {
            onBatchLoaded(pending.toList())
        }
        return result
    }

    private data class CachedCatalog(
        val packageName: String,
        val createdAtElapsedMs: Long,
        val apps: List<InstalledAppInfo>,
    )

    private companion object {
        private const val CACHE_TTL_MS = 60_000L
        private val cacheMutex = Mutex()

        @Volatile
        private var cachedCatalog: CachedCatalog? = null
    }
}