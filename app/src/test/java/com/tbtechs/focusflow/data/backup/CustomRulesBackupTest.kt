package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.MatchMode
import com.tbtechs.focusflow.enforcement.customrules.NodeMatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRulesBackupTest {
    private val own = "com.tbtechs.focusflow"

    private fun rule(
        name: String = "Reels",
        packageName: String = "com.instagram.android",
        viewId: String = "clips_viewer",
        enabled: Boolean = false,
        id: String = "id-$name",
        mode: MatchMode = MatchMode.ALL,
    ) = CustomScreenRule(
        id = id,
        name = name,
        packageName = packageName,
        enabled = enabled,
        matchMode = mode,
        matchers = listOf(NodeMatcher(viewIdSuffix = viewId)),
    )

    private fun settingsWith(rules: List<CustomScreenRule>): JsonObject =
        JsonObject(mapOf(CustomRulesBackup.SETTINGS_KEY to CustomRulesBackup.toJson(rules)!!))

    @Test
    fun noRulesMeansNoKeyInTheBackup() {
        assertNull(CustomRulesBackup.toJson(emptyList()))
    }

    @Test
    fun exportKeepsEnabledAndEverythingElse() {
        val json = CustomRulesBackup.toJson(listOf(rule(enabled = true, mode = MatchMode.ANY)))!!
        val first = json.first() as JsonObject
        assertEquals(JsonPrimitive(true), first["enabled"])
        assertEquals(JsonPrimitive("Reels"), first["name"])
        assertEquals(JsonPrimitive("ANY"), first["matchMode"])
    }

    @Test
    fun missingKeyIsNotAnInstruction() {
        val plan = CustomRulesBackup.plan(JsonObject(emptyMap()), listOf(rule()), own)
        assertNull("A backup without rules must not touch the rules on this phone", plan.merged)
        assertEquals(emptyList<String>(), plan.warnings)
    }

    @Test
    fun restoreOnAnEmptyPhoneAddsRulesAndKeepsEnabled() {
        val plan = CustomRulesBackup.plan(
            settingsWith(listOf(rule("A", viewId = "a", enabled = true), rule("B", viewId = "b"))),
            emptyList(),
            own,
        )
        val merged = plan.merged!!
        assertEquals(listOf("A", "B"), merged.map { it.name })
        assertEquals(listOf(true, false), merged.map { it.enabled })
        assertEquals(2, plan.added)
    }

    @Test
    fun restoredRulesGetNewIdsSoTheyCannotClashWithExistingOnes() {
        val existing = listOf(rule("Mine", viewId = "mine", id = "same-id"))
        val plan = CustomRulesBackup.plan(
            settingsWith(listOf(rule("Theirs", viewId = "theirs", id = "same-id"))),
            existing,
            own,
        )
        val merged = plan.merged!!
        assertEquals(2, merged.size)
        assertEquals(2, merged.map { it.id }.toSet().size)
        assertEquals("same-id", merged[0].id)
    }

    @Test
    fun existingRulesAreKeptUntouchedIncludingEnabled() {
        val existing = listOf(rule("Mine", viewId = "mine", enabled = true))
        val plan = CustomRulesBackup.plan(
            settingsWith(listOf(rule("Theirs", viewId = "theirs"))),
            existing,
            own,
        )
        assertEquals(existing[0], plan.merged!![0])
    }

    @Test
    fun aRuleThatIsAlreadyHereIsNotAddedAgain() {
        val existing = listOf(rule("Mine", enabled = true))
        val plan = CustomRulesBackup.plan(
            settingsWith(listOf(rule("Renamed copy", enabled = false))),
            existing,
            own,
        )
        assertEquals(existing, plan.merged)
        assertEquals(0, plan.added)
        assertEquals(1, plan.alreadyThere)
    }

    @Test
    fun restoringTheSameBackupTwiceChangesNothingTheSecondTime() {
        val settings = settingsWith(listOf(rule("A", viewId = "a"), rule("B", viewId = "b")))
        val first = CustomRulesBackup.plan(settings, emptyList(), own).merged!!
        val second = CustomRulesBackup.plan(settings, first, own)
        assertEquals(first, second.merged)
        assertEquals(0, second.added)
        assertEquals(2, second.alreadyThere)
    }

    @Test
    fun theLimitOfThirtyRulesHoldsAcrossExistingAndRestored() {
        val existing = (1..28).map { rule("E$it", viewId = "e$it") }
        val incoming = (1..5).map { rule("I$it", viewId = "i$it") }
        val plan = CustomRulesBackup.plan(settingsWith(incoming), existing, own)
        assertEquals(CustomScreenRules.MAX_RULES, plan.merged!!.size)
        assertEquals(2, plan.added)
        assertEquals(3, plan.skipped)
        assertTrue(plan.warnings.any { it.contains("30") })
    }

    @Test
    fun anUnsafeRuleInTheFileIsSkippedAndTheRestStillComeIn() {
        val plan = CustomRulesBackup.plan(
            settingsWith(
                listOf(
                    rule("Settings", packageName = "com.android.settings", viewId = "x"),
                    rule("Own app", packageName = own, viewId = "y"),
                    rule("Good", viewId = "good"),
                ),
            ),
            emptyList(),
            own,
        )
        assertEquals(listOf("Good"), plan.merged!!.map { it.name })
        assertEquals(2, plan.skipped)
    }

    @Test
    fun aMatcherThatWouldMatchEverythingIsSkipped() {
        val broad = CustomScreenRule(
            id = "b", name = "Broad", packageName = "com.x.y", enabled = true,
            matchMode = MatchMode.ALL, matchers = listOf(NodeMatcher(className = "android.widget.Button")),
        )
        val plan = CustomRulesBackup.plan(settingsWith(listOf(broad)), emptyList(), own)
        assertEquals(0, plan.added)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun wrongShapeGivesAWarningAndLeavesRulesAlone() {
        val notAList = JsonObject(mapOf(CustomRulesBackup.SETTINGS_KEY to JsonPrimitive("oops")))
        val plan = CustomRulesBackup.plan(notAList, listOf(rule()), own)
        assertNull(plan.merged)
        assertEquals(1, plan.warnings.size)
    }

    @Test
    fun anEmptyListChangesNothing() {
        val empty = JsonObject(mapOf(CustomRulesBackup.SETTINGS_KEY to JsonArray(emptyList())))
        val plan = CustomRulesBackup.plan(empty, listOf(rule()), own)
        assertNull(plan.merged)
    }

    @Test
    fun entriesThatAreNotObjectsAreSkippedNotFatal() {
        val mixed = JsonObject(
            mapOf(
                CustomRulesBackup.SETTINGS_KEY to buildJsonArray {
                    add(JsonPrimitive(5))
                    add(
                        buildJsonObject {
                            put("name", "Ok")
                            put("packageName", "com.a.b")
                            put("matchers", buildJsonArray { add(buildJsonObject { put("viewIdSuffix", "v") }) })
                        },
                    )
                },
            ),
        )
        val plan = CustomRulesBackup.plan(mixed, emptyList(), own)
        assertEquals(listOf("Ok"), plan.merged!!.map { it.name })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun theKeyIsNotMistakenForAPortableSetting() {
        assertFalse(PortableSettingsPolicy.isPortableBackupKey(CustomRulesBackup.SETTINGS_KEY))
        assertNotNull(CustomRulesBackup.SETTINGS_KEY)
    }
}
