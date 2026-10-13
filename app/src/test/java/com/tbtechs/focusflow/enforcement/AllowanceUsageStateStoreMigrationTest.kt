package com.tbtechs.focusflow.enforcement

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceUsageStateStoreMigrationTest {
    @Test
    fun clearsRetiredAllowanceKeysAndKeepsLiveSessionMarkers() {
        val prefs = MemorySharedPreferences()
        prefs.edit()
            .putLong("daily_allowance_usage_stats_sync", 1_000L)
            .putLong("active_session_open_at_ms", 2_000L)
            .putString("active_session_pkg", "com.example.target")
            .putLong("active_session_last_checkpoint_ms", 3_000L)
            .putLong("active_session_end_ms", 4_000L)
            .apply()
        val ledger = AllowanceLedger(
            object : AllowanceLedgerStore {
                override fun readUsageJson(): String? = null
                override fun writeUsageJson(value: String) = Unit
            },
        )

        AllowanceUsageStateStore(prefs, ledger).clearRetiredPreferenceKeys()

        assertFalse(prefs.contains("daily_allowance_usage_stats_sync"))
        assertFalse(prefs.contains("active_session_open_at_ms"))
        assertEquals("com.example.target", prefs.getString("active_session_pkg", null))
        assertEquals(3_000L, prefs.getLong("active_session_last_checkpoint_ms", 0L))
        assertEquals(4_000L, prefs.getLong("active_session_end_ms", 0L))
        assertTrue(prefs.contains("active_session_pkg"))
    }
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

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toMutableSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            put(key, value)

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
