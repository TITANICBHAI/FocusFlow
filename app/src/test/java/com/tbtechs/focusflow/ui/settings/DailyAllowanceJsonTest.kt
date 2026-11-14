package com.tbtechs.focusflow.ui.settings

import com.tbtechs.focusflow.enforcement.allowancePackage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAllowanceJsonTest {
    @Test
    fun entriesSavedWithEitherPackageKeyAreRead() {
        val raw = """[
            {"package":"a.app","mode":"time_budget","budgetMinutes":20},
            {"packageName":"b.app","mode":"count","countPerDay":3},
            {"package":"c.app","packageName":"c.app","mode":"count"},
            {"mode":"count"}
        ]"""
        val entries = dailyAllowanceEntriesFromJson(raw)
        assertEquals(listOf("a.app", "b.app", "c.app"), entries.map { it.packageName })
        assertEquals(20, entries[0].budgetMinutes)
        assertEquals(3, entries[1].countPerDay)
    }

    @Test
    fun allowancePackagePrefersPackageNameAndFallsBackToPackage() {
        assertEquals("x.app", JSONObject("""{"packageName":"x.app","package":"y.app"}""").allowancePackage())
        assertEquals("y.app", JSONObject("""{"package":"y.app"}""").allowancePackage())
        assertEquals("", JSONObject("""{"mode":"count"}""").allowancePackage())
    }
}
