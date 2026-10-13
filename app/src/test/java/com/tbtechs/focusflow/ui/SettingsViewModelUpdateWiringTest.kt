package com.tbtechs.focusflow.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.domain.PinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelUpdateWiringTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rejectedNetworkDisableDoesNotEscapeUpdateSettingsOrDiscardOtherChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = MemorySharedPreferences().apply {
            edit()
                .putBoolean("net_block_enabled", true)
                .putBoolean("net_block_vpn", true)
                .putBoolean("focus_active", true)
                .putBoolean("net_block_explicit_migrated", true)
                .apply()
        }
        val context = TestContext(preferences)
        val repository = SettingsRepository(
            context,
            requestVpnSyncAction = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val viewModel = SettingsViewModel(repository, PinManager(context), context)
        val store = ViewModelStore().apply { put("settings", viewModel) }

        try {
            advanceUntilIdle()
            val current = viewModel.settings.value
            assertTrue(current.networkBlockEnabled)

            viewModel.updateSettings(
                current.copy(
                    networkBlockEnabled = false,
                    darkModeEnabled = true,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.settings.value.networkBlockEnabled)
            assertTrue(viewModel.settings.value.darkModeEnabled)
            assertTrue(preferences.getBoolean("net_block_enabled", false))
            assertTrue(preferences.getBoolean("dark_mode_enabled", false))
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun defenseEditPreservesNewerStoredSelfHealValueThroughViewModelAndRepository() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = MemorySharedPreferences().apply {
            edit().putBoolean("net_block_explicit_migrated", true).apply()
        }
        val context = TestContext(preferences)
        val repository = SettingsRepository(
            context,
            requestVpnSyncAction = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val viewModel = SettingsViewModel(repository, PinManager(context), context)
        val store = ViewModelStore().apply { put("settings", viewModel) }

        try {
            advanceUntilIdle()
            val loaded = viewModel.settings.value
            assertFalse(loaded.vpnSelfHealEnabled)

            // Simulate another app path saving self-heal after this settings snapshot loaded.
            preferences.edit().putBoolean("net_block_self_heal", true).apply()
            viewModel.updateSettings(loaded.copy(focusMirrorVpnEnabled = true))
            advanceUntilIdle()

            assertTrue(preferences.getBoolean("net_block_self_heal", false))
            assertTrue(viewModel.settings.value.vpnSelfHealEnabled)
            assertTrue(viewModel.settings.value.focusMirrorVpnEnabled)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun standaloneVpnPackagesArePersistedOnSaveAndClearedWhenTheBlockEnds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = MemorySharedPreferences().apply {
            edit().putBoolean("net_block_explicit_migrated", true).apply()
        }
        val context = TestContext(preferences)
        val repository = SettingsRepository(
            context,
            requestVpnSyncAction = {},
            ioDispatcher = StandardTestDispatcher(testScheduler),
            pushWidgetUpdateAction = {},
            allowanceConfigChangedBroadcastAction = {},
        )
        val viewModel = SettingsViewModel(repository, PinManager(context), context)
        val store = ViewModelStore().apply { put("settings", viewModel) }
        val vpnPackages = listOf("com.example.networkA", "com.example.networkB")

        try {
            advanceUntilIdle()
            viewModel.setStandaloneBlockAndAllowance(
                StandaloneBlockAndAllowanceConfig(
                    standaloneBlockActive = true,
                    standaloneBlockPackages = listOf("com.example.blocked"),
                    standaloneBlockUntilMs = System.currentTimeMillis() + 60_000L,
                    allowanceEntries = emptyList(),
                    standaloneBlockVpnPackages = vpnPackages,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                """["com.example.networkA","com.example.networkB"]""",
                preferences.getString("net_block_standalone_vpn_packages", null),
            )
            assertEquals(vpnPackages, viewModel.settings.value.standaloneBlockVpnPackages)

            viewModel.setStandaloneBlockAndAllowance(
                StandaloneBlockAndAllowanceConfig(
                    standaloneBlockActive = false,
                    standaloneBlockPackages = emptyList(),
                    standaloneBlockUntilMs = 0L,
                    allowanceEntries = emptyList(),
                    standaloneBlockVpnPackages = vpnPackages,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "[]",
                preferences.getString("net_block_standalone_vpn_packages", null),
            )
            assertEquals(emptyList<String>(), viewModel.settings.value.standaloneBlockVpnPackages)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private class TestContext(
    private val preferences: SharedPreferences,
) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences

    override fun getPackageName(): String = "com.tbtechs.focusflow.test"

    override fun sendBroadcast(intent: Intent) = Unit
}

private class MemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = MemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any>()
        private val removals = linkedSetOf<String>()
        private var clearRequested = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            if (value == null) {
                updates.remove(key)
                removals.add(key)
            } else {
                updates[key] = value
                removals.remove(key)
            }
            return this
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toMutableSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            updates.remove(key)
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            values.putAll(updates)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
