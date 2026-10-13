package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.missingInstalledAppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class InstalledAppsLoadState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val loading: Boolean = true,
    val error: Throwable? = null,
)

/**
 * Shared progressive app catalog loader.
 *
 * This owns only discovery state. Allowances, VPN selections, Always-On selections, and
 * Allowed During Focus selections stay in their respective feature screens.
 */
@Composable
fun rememberInstalledApps(
    repository: InstalledAppsRepository,
    batchSize: Int = 24,
    refreshToken: Int = 0,
): InstalledAppsLoadState {
    var state by remember(repository, batchSize) {
        mutableStateOf(InstalledAppsLoadState())
    }
    var lastRefreshToken by remember(repository, batchSize) {
        mutableStateOf(refreshToken)
    }

    LaunchedEffect(repository, batchSize, refreshToken) {
        if (lastRefreshToken != refreshToken) {
            repository.invalidateCache()
            lastRefreshToken = refreshToken
        }
        state = InstalledAppsLoadState()
        try {
            withContext(Dispatchers.IO) {
                repository.getInstalledAppsBatched(batchSize) { batch ->
                    withContext(Dispatchers.Main.immediate) {
                        state = state.copy(
                            apps = (state.apps + batch)
                                .distinctBy { it.packageName }
                                .sortedBy { it.appName.lowercase() },
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            state = state.copy(error = exception)
        } finally {
            if (currentCoroutineContext().isActive) {
                state = state.copy(loading = false)
            }
        }
    }

    return state
}

/**
 * Resolve a saved package selection without presenting a package-id fragment as an app name.
 * Returns null while the catalog is still loading and an explicit missing/unavailable record
 * after loading completes.
 */
fun InstalledAppsLoadState.resolve(packageName: String): InstalledAppInfo? {
    apps.firstOrNull { it.packageName == packageName }?.let { return it }
    if (loading) return null
    return missingInstalledAppInfo(
        packageName = packageName,
        label = if (error == null) "App not installed" else "App details unavailable",
    )
}
