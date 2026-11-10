package com.tbtechs.focusflow.enforcement.customrules

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomScreenRuleTest {
    private val own = "com.tbtechs.focusflow"

    private fun rule(
        name: String = "Reels",
        packageName: String = "com.instagram.android",
        matchers: List<NodeMatcher> = listOf(NodeMatcher(viewIdSuffix = "clips_viewer")),
        enabled: Boolean = false,
        matchMode: MatchMode = MatchMode.ALL,
        id: String = "id-1",
    ) = CustomScreenRule(
        id = id,
        name = name,
        packageName = packageName,
        enabled = enabled,
        matchMode = matchMode,
        matchers = matchers,
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        val rules = listOf(
            rule(
                enabled = true,
                matchMode = MatchMode.ANY,
                matchers = listOf(
                    NodeMatcher(viewIdSuffix = "reel_recycler", className = "androidx.RecyclerView"),
                    NodeMatcher(textContains = "Reels", descContains = "Reels tab"),
                ),
            ),
            rule(id = "id-2", name = "Shorts", packageName = "com.google.android.youtube"),
        )
        assertEquals(rules, CustomScreenRules.fromJson(CustomScreenRules.toJson(rules)))
    }

    @Test
    fun fromJsonOfGarbageIsEmptyAndNeverThrows() {
        assertTrue(CustomScreenRules.fromJson("garbage").isEmpty())
        assertTrue(CustomScreenRules.fromJson("").isEmpty())
        assertTrue(CustomScreenRules.fromJson("{\"a\":1}").isEmpty())
    }

    @Test
    fun fromJsonSkipsBadEntriesAndKeepsGoodOnes() {
        val good = CustomScreenRules.toJson(listOf(rule()))
        val arr = JSONArray(good)
        arr.put("not an object")
        arr.put(JSONObject().put("name", "no id"))
        arr.put(JSONObject().put("id", "id-9").put("name", "Second").put("packageName", "a.b"))
        val parsed = CustomScreenRules.fromJson(arr.toString())
        assertEquals(listOf("id-1", "id-9"), parsed.map { it.id })
    }

    @Test
    fun jsonNullFieldIsNotTheWordNull() {
        val json = """[{"id":"x","name":"N","packageName":"a.b","matchers":[{"viewIdSuffix":null,"textContains":"hi"}]}]"""
        val m = CustomScreenRules.fromJson(json).single().matchers.single()
        assertNull(m.viewIdSuffix)
        assertEquals("hi", m.textContains)
    }

    @Test
    fun validateAcceptsAGoodRule() {
        assertNull(CustomScreenRules.validate(rule(), own))
    }

    @Test
    fun validateRejectsBlankNameAndPackage() {
        assertNotNull(CustomScreenRules.validate(rule(name = " "), own))
        assertNotNull(CustomScreenRules.validate(rule(packageName = ""), own))
    }

    @Test
    fun validateRejectsOwnAndSystemPackages() {
        assertNotNull(CustomScreenRules.validate(rule(packageName = own), own))
        assertNotNull(CustomScreenRules.validate(rule(packageName = "com.android.settings"), own))
        assertNotNull(CustomScreenRules.validate(rule(packageName = "com.android.systemui"), own))
        assertNotNull(CustomScreenRules.validate(rule(packageName = " com.android.settings "), own))
    }

    @Test
    fun validateRejectsMatcherProblems() {
        assertNotNull(CustomScreenRules.validate(rule(matchers = emptyList()), own))
        assertNotNull(CustomScreenRules.validate(rule(matchers = listOf(NodeMatcher())), own))
        assertNotNull(
            CustomScreenRules.validate(rule(matchers = listOf(NodeMatcher(className = "android.widget.ImageView"))), own),
        )
        val six = List(CustomScreenRules.MAX_MATCHERS + 1) { NodeMatcher(textContains = "t$it") }
        assertNotNull(CustomScreenRules.validate(rule(matchers = six), own))
        val five = List(CustomScreenRules.MAX_MATCHERS) { NodeMatcher(textContains = "t$it") }
        assertNull(CustomScreenRules.validate(rule(matchers = five), own))
    }

    @Test
    fun validateRejectsLongFields() {
        val long = "x".repeat(CustomScreenRules.MAX_FIELD_LEN + 1)
        assertNotNull(CustomScreenRules.validate(rule(matchers = listOf(NodeMatcher(textContains = long))), own))
        assertNotNull(CustomScreenRules.validate(rule(name = long), own))
        val ok = "x".repeat(CustomScreenRules.MAX_FIELD_LEN)
        assertNull(CustomScreenRules.validate(rule(matchers = listOf(NodeMatcher(textContains = ok))), own))
    }

    @Test
    fun exportEnvelopeHasFormatAndVersion() {
        val root = JSONObject(CustomScreenRules.exportEnvelope(listOf(rule())))
        assertEquals("focusflow.screenrules", root.getString("format"))
        assertEquals(1, root.getInt("version"))
        assertEquals(1, root.getJSONArray("rules").length())
    }

    @Test
    fun importForcesDisabledAndNewIds() {
        val text = CustomScreenRules.exportEnvelope(listOf(rule(enabled = true, id = "keep-me")))
        val result = CustomScreenRules.parseImport(text, own)
        assertTrue(result.rejected.isEmpty())
        val r = result.accepted.single()
        assertFalse(r.enabled)
        assertNotEquals("keep-me", r.id)
        assertTrue(r.id.isNotBlank())
        assertEquals("Reels", r.name)
    }

    @Test
    fun importGivesEachRuleADifferentId() {
        val text = CustomScreenRules.exportEnvelope(listOf(rule(id = "same"), rule(id = "same", name = "Other")))
        val ids = CustomScreenRules.parseImport(text, own).accepted.map { it.id }
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun importRejectsWrongFormatAndVersionAndGarbage() {
        val wrongFormat = JSONObject().put("format", "other").put("version", 1).put("rules", JSONArray()).toString()
        val wrongVersion = JSONObject().put("format", "focusflow.screenrules").put("version", 2).put("rules", JSONArray()).toString()
        for (text in listOf(wrongFormat, wrongVersion, "garbage", "[]", "")) {
            val result = CustomScreenRules.parseImport(text, own)
            assertTrue(text, result.accepted.isEmpty())
            assertTrue(text, result.rejected.isNotEmpty())
        }
    }

    @Test
    fun importRejectsOversizeText() {
        val padding = " ".repeat(CustomScreenRules.MAX_IMPORT_CHARS)
        val text = CustomScreenRules.exportEnvelope(listOf(rule())) + padding
        val result = CustomScreenRules.parseImport(text, own)
        assertTrue(result.accepted.isEmpty())
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun importCapsAtMaxRules() {
        val many = List(CustomScreenRules.MAX_RULES + 5) { rule(id = "i$it", name = "Rule $it") }
        val result = CustomScreenRules.parseImport(CustomScreenRules.exportEnvelope(many), own)
        assertEquals(CustomScreenRules.MAX_RULES, result.accepted.size)
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun importListsInvalidRulesWithTheirName() {
        val text = CustomScreenRules.exportEnvelope(
            listOf(
                rule(name = "Good"),
                rule(name = "Sneaky", packageName = "com.android.settings"),
                rule(name = "Broad", matchers = listOf(NodeMatcher(className = "android.widget.ImageView"))),
            ),
        )
        val result = CustomScreenRules.parseImport(text, own)
        assertEquals(listOf("Good"), result.accepted.map { it.name })
        assertEquals(2, result.rejected.size)
        assertTrue(result.rejected[0].startsWith("Sneaky: "))
        assertTrue(result.rejected[1].startsWith("Broad: "))
    }

    @Test
    fun selectedOnlySurvivesJsonAndExportImport() {
        val rules = listOf(
            rule(matchers = listOf(NodeMatcher(descContains = "Reels", selectedOnly = true))),
        )
        assertEquals(rules, CustomScreenRules.fromJson(CustomScreenRules.toJson(rules)))

        val imported = CustomScreenRules.parseImport(CustomScreenRules.exportEnvelope(rules), own)
        assertTrue(imported.rejected.isEmpty())
        assertTrue(imported.accepted.single().matchers.single().selectedOnly)
    }

    @Test
    fun selectedOnlyIsOffWhenMissingAndNotWrittenWhenOff() {
        val old = """[{"id":"a","name":"n","packageName":"p","matchers":[{"viewIdSuffix":"x"}]}]"""
        assertFalse(CustomScreenRules.fromJson(old).single().matchers.single().selectedOnly)
        val written = CustomScreenRules.toJson(listOf(rule()))
        assertFalse(written.contains("selectedOnly"))
    }
}
