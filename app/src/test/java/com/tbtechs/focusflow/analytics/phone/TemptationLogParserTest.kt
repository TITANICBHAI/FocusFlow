package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.TemptationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemptationLogParserTest {

    @Test
    fun parsesValidEntriesInOrder() {
        val raw = """[{"pkg":"com.a","appName":"A","timestamp":1000},{"pkg":"com.b","appName":"B","timestamp":2000}]"""
        assertEquals(
            listOf(TemptationEntry("com.a", "A", 1000L), TemptationEntry("com.b", "B", 2000L)),
            TemptationLogParser.parse(raw),
        )
    }

    @Test
    fun emptyArrayAndBlankTextGiveEmptyList() {
        assertTrue(TemptationLogParser.parse("[]").isEmpty())
        assertTrue(TemptationLogParser.parse("").isEmpty())
        assertTrue(TemptationLogParser.parse("   ").isEmpty())
    }

    @Test
    fun malformedJsonGivesEmptyList() {
        assertTrue(TemptationLogParser.parse("{not json").isEmpty())
        assertTrue(TemptationLogParser.parse("""[{"pkg":"com.a","timestamp":""").isEmpty())
        assertTrue(TemptationLogParser.parse("null").isEmpty())
    }

    @Test
    fun anObjectInsteadOfAnArrayGivesEmptyList() {
        assertTrue(TemptationLogParser.parse("""{"pkg":"com.a","timestamp":5}""").isEmpty())
    }

    @Test
    fun itemsWithoutAUsablePkgAreSkipped() {
        val raw = """[{"appName":"X","timestamp":1},{"pkg":"","timestamp":2},{"pkg":"   ","timestamp":3},""" +
            """{"pkg":null,"timestamp":4},{"pkg":"com.ok","timestamp":5}]"""
        assertEquals(listOf(TemptationEntry("com.ok", null, 5L)), TemptationLogParser.parse(raw))
    }

    @Test
    fun missingOrBlankAppNameBecomesNull() {
        val raw = """[{"pkg":"a","timestamp":1},{"pkg":"b","appName":"","timestamp":2},""" +
            """{"pkg":"c","appName":"  ","timestamp":3},{"pkg":"d","appName":null,"timestamp":4}]"""
        assertTrue(TemptationLogParser.parse(raw).all { it.appName == null })
        assertEquals(4, TemptationLogParser.parse(raw).size)
    }

    @Test
    fun missingTimestampBecomesZero() {
        assertEquals(0L, TemptationLogParser.parse("""[{"pkg":"a"}]""").single().timestamp)
    }

    @Test
    fun nonObjectElementsAreSkipped() {
        val raw = """[1,"x",null,[],{"pkg":"a","timestamp":9}]"""
        assertEquals(listOf(TemptationEntry("a", null, 9L)), TemptationLogParser.parse(raw))
    }

    @Test
    fun largeEpochMillisKeepPrecision() {
        val ts = 1_793_000_000_123L
        assertEquals(ts, TemptationLogParser.parse("""[{"pkg":"a","timestamp":$ts}]""").single().timestamp)
        assertNull(TemptationLogParser.parse("""[{"pkg":"a","timestamp":$ts}]""").single().appName)
    }
}
