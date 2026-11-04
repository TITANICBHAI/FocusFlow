package com.tbtechs.focusflow.ui.stats

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyLimitTest {
    @Test
    fun addsALimitWithoutTouchingOtherApps() {
        val existing = """[{"package":"a.app","dailyAllowanceMs":600000,"mode":"time_budget","budgetMinutes":10}]"""
        val merged = JSONArray(DailyLimit.merge(existing, "b.app", 45))
        assertEquals(2, merged.length())
        assertEquals("a.app", merged.getJSONObject(0).getString("package"))
        val added = merged.getJSONObject(1)
        assertEquals("b.app", added.getString("package"))
        assertEquals("b.app", added.getString("packageName"))
        assertEquals(45, added.getInt("budgetMinutes"))
        assertEquals(45 * 60_000L, added.getLong("dailyAllowanceMs"))
        assertEquals("time_budget", added.getString("mode"))
    }

    @Test
    fun replacesAnExistingLimitForTheSameApp() {
        val existing = """[{"package":"b.app","dailyAllowanceMs":600000,"mode":"time_budget","budgetMinutes":10}]"""
        val merged = JSONArray(DailyLimit.merge(existing, "b.app", 30))
        assertEquals(1, merged.length())
        assertEquals(30, merged.getJSONObject(0).getInt("budgetMinutes"))
    }

    @Test
    fun startsFromNothingAndKeepsTheMinutesSane() {
        assertEquals(1, JSONArray(DailyLimit.merge(null, "c.app", 30)).length())
        assertEquals(5, JSONArray(DailyLimit.merge("not json", "c.app", 1)).getJSONObject(0).getInt("budgetMinutes"))
        assertEquals(600, JSONArray(DailyLimit.merge("[]", "c.app", 9999)).getJSONObject(0).getInt("budgetMinutes"))
    }

    @Test
    fun anEntryWrittenByEitherKeyShapeIsReplacedNotDuplicated() {
        val existing = """[{"packageName":"b.app","budgetMinutes":10},{"package":"c.app","budgetMinutes":20}]"""
        val merged = JSONArray(DailyLimit.merge(existing, "b.app", 30))
        assertEquals(2, merged.length())
        assertEquals("c.app", merged.getJSONObject(0).getString("package"))
        assertEquals(30, merged.getJSONObject(1).getInt("budgetMinutes"))
    }
}
