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
        val text = CustomScreenRules.exportEnvelope(
            listOf(
                rule(id = "same"),
                rule(id = "same", name = "Other", matchers = listOf(NodeMatcher(viewIdSuffix = "other_view"))),
            ),
        )
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
        val many = List(CustomScreenRules.MAX_RULES + 5) {
            rule(id = "i$it", name = "Rule $it", matchers = listOf(NodeMatcher(viewIdSuffix = "view_$it")))
        }
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

    // ----- duplicates -----

    private fun m(
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        cls: String? = null,
        selected: Boolean = false,
    ) = NodeMatcher(viewIdSuffix = id, className = cls, textContains = text, descContains = desc, selectedOnly = selected)

    @Test
    fun sameRuleIgnoresNameIdEnabledCaseAndMatcherOrder() {
        val a = rule(name = "One", id = "a", enabled = true, matchers = listOf(m(id = "Reel_Tab"), m(desc = "Reels")))
        val b = rule(
            name = "Two", id = "b", enabled = false, packageName = " COM.Instagram.Android ",
            matchers = listOf(m(desc = "REELS"), m(id = " reel_tab ")),
        )
        assertTrue(CustomScreenRules.isSameRule(a, b))
        assertTrue(CustomScreenRules.isSameRule(b, a))
    }

    @Test
    fun sameRuleNeedsTheSameAppMatchersSelectedFlagAndMode() {
        val base = rule(matchers = listOf(m(id = "a"), m(text = "b")))
        assertFalse(CustomScreenRules.isSameRule(base, base.copy(packageName = "com.other")))
        assertFalse(CustomScreenRules.isSameRule(base, base.copy(matchers = listOf(m(id = "a"), m(text = "c")))))
        assertFalse(CustomScreenRules.isSameRule(base, base.copy(matchers = listOf(m(id = "a")))))
        assertFalse(
            CustomScreenRules.isSameRule(base, base.copy(matchers = listOf(m(id = "a", selected = true), m(text = "b")))),
        )
        assertFalse(CustomScreenRules.isSameRule(base, base.copy(matchMode = MatchMode.ANY)))
    }

    @Test
    fun sameRuleTreatsAllAndAnyAsEqualForOneMatcherAndRepeatedMatchersAsOne() {
        val one = rule(matchers = listOf(m(id = "a")), matchMode = MatchMode.ALL)
        assertTrue(CustomScreenRules.isSameRule(one, one.copy(matchMode = MatchMode.ANY)))
        assertTrue(CustomScreenRules.isSameRule(one, one.copy(matchers = listOf(m(id = "a"), m(id = "A")))))
    }

    @Test
    fun sameRuleKeepsSpacesInTextAndDescription() {
        val a = rule(matchers = listOf(m(text = "Reels")))
        assertFalse(CustomScreenRules.isSameRule(a, rule(matchers = listOf(m(text = "Reels ")))))
        assertFalse(CustomScreenRules.isSameRule(a, rule(matchers = listOf(m(desc = "Reels")))))
    }

    @Test
    fun importSkipsRulesYouAlreadyHaveEvenWithAnotherName() {
        val mine = rule(name = "Mine", id = "mine", enabled = true)
        val text = CustomScreenRules.exportEnvelope(listOf(rule(name = "Theirs", id = "t")))
        val result = CustomScreenRules.parseImport(text, own, listOf(mine))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.rejected.isEmpty())
        assertEquals(1, result.duplicates.size)
        assertTrue(result.duplicates[0].startsWith("Theirs: "))
        assertTrue(result.duplicates[0].contains("\"Mine\""))
    }

    @Test
    fun importingTheSameFileTwiceAddsNothingSecondTime() {
        val text = CustomScreenRules.exportEnvelope(
            listOf(rule(name = "A"), rule(name = "B", matchers = listOf(m(id = "other")))),
        )
        val first = CustomScreenRules.parseImport(text, own)
        assertEquals(2, first.accepted.size)
        val second = CustomScreenRules.parseImport(text, own, first.accepted)
        assertTrue(second.accepted.isEmpty())
        assertEquals(2, second.duplicates.size)
    }

    @Test
    fun importSkipsARepeatedRuleInsideTheFileAndKeepsTheFirst() {
        val text = CustomScreenRules.exportEnvelope(
            listOf(rule(name = "First", id = "1"), rule(name = "Second", id = "2"), rule(name = "Third", id = "3", packageName = "com.other")),
        )
        val result = CustomScreenRules.parseImport(text, own)
        assertEquals(listOf("First", "Third"), result.accepted.map { it.name })
        assertEquals(1, result.duplicates.size)
        assertTrue(result.duplicates[0].contains("\"First\""))
    }

    @Test
    fun invalidRulesAreReportedAsInvalidNotAsDuplicates() {
        val bad = rule(name = "Sneaky", packageName = "com.android.settings")
        val text = CustomScreenRules.exportEnvelope(listOf(bad, bad.copy(name = "Sneaky again")))
        val result = CustomScreenRules.parseImport(text, own, listOf(bad))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.duplicates.isEmpty())
        assertEquals(2, result.rejected.size)
    }

    @Test
    fun importWithoutExistingRulesBehavesAsBefore() {
        val text = CustomScreenRules.exportEnvelope(listOf(rule(name = "Good")))
        val result = CustomScreenRules.parseImport(text, own)
        assertEquals(1, result.accepted.size)
        assertTrue(result.duplicates.isEmpty())
    }

    // ----- pasted text, strict reading and size -----

    private val goodRuleJson =
        """{"name":"Reels","packageName":"com.instagram.android","matchMode":"ALL","matchers":[{"descContains":"Reels","selectedOnly":true}]}"""
    private val goodEnvelope = """{"format":"focusflow.screenrules","version":1,"rules":[$goodRuleJson]}"""

    private fun assertImports(text: String, name: String = "Reels") {
        val result = CustomScreenRules.parseImport(text, own)
        assertTrue(text + " -> " + result.rejected, result.rejected.isEmpty())
        assertEquals(name, result.accepted.single().name)
        assertTrue(result.accepted.single().matchers.single().selectedOnly)
    }

    @Test
    fun importReadsAnExportWrappedInACodeBlock() {
        assertImports("```json\n$goodEnvelope\n```")
        assertImports("```\n$goodEnvelope\n```")
        assertImports("Here is the rule:\n```JSON\n$goodEnvelope\n```\nPaste it into FocusFlow.")
    }

    @Test
    fun importReadsJsonWithSentencesAroundIt() {
        assertImports("Sure! $goodEnvelope Let me know if it works.")
    }

    @Test
    fun importIgnoresByteOrderMarkNonBreakingSpacesAndZeroWidthCharacters() {
        assertImports("\uFEFF$goodEnvelope")
        assertImports(goodEnvelope.replace("{\"format\"", "{\u00A0\"format\""))
        assertImports(goodEnvelope.replace("\"rules\"", "\u200B\"rules\""))
    }

    @Test
    fun importFixesCurlyQuotesAddedByChatApps() {
        assertImports(goodEnvelope.replace('"', '\u201D'))
        val curly = goodEnvelope.replace("\"format\"", "\u201Cformat\u201D").replace("\"focusflow.screenrules\"", "\u201Cfocusflow.screenrules\u201D")
        assertImports(curly)
    }

    @Test
    fun importDoesNotChangeAFileThatIsAlreadyValid() {
        // Real curly quotes inside the values, with every structural quote straight: valid as it is.
        val text = "{\"format\":\"focusflow.screenrules\",\"version\":1,\"rules\":[{\"name\":\"Say \u201Chi\u201D\"," +
            "\"packageName\":\"com.a\",\"matchers\":[{\"textContains\":\"it\u2019s\"}]}]}"
        val r = CustomScreenRules.parseImport(text, own).accepted.single()
        assertEquals("Say \u201Chi\u201D", r.name)
        assertEquals("it\u2019s", r.matchers.single().textContains)
    }

    @Test
    fun importAcceptsABareListAndASingleRule() {
        assertImports("[$goodRuleJson]")
        assertImports(goodRuleJson)
        assertImports("```json\n[$goodRuleJson]\n```")
    }

    @Test
    fun importStillRefusesThingsThatAreNotRules() {
        for (text in listOf("hello", "{}", """{"a":1}""", "[]", "[1,2]", "```\nnot json\n```", """{"format":"focusflow.screenrules","version":1,"rules":[]}""")) {
            val result = CustomScreenRules.parseImport(text, own)
            assertTrue(text, result.accepted.isEmpty())
            assertTrue(text, result.rejected.isNotEmpty())
        }
    }

    @Test
    fun importTellsYouWhenTheFileIsFromANewerApp() {
        val newer = goodEnvelope.replace("\"version\":1", "\"version\":2")
        val result = CustomScreenRules.parseImport(newer, own)
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.rejected.single().contains("newer version"))
    }

    @Test
    fun matchModeIsReadIgnoringCaseAndUnknownModesAreRefused() {
        val any = goodEnvelope.replace("\"ALL\"", "\"any\"")
        assertEquals(MatchMode.ANY, CustomScreenRules.parseImport(any, own).accepted.single().matchMode)
        val bad = goodEnvelope.replace("\"ALL\"", "\"SOME\"")
        val result = CustomScreenRules.parseImport(bad, own)
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.rejected.single().startsWith("Reels: Match mode must be ALL or ANY"))
    }

    @Test
    fun aMatcherWithAMisspelledFieldRefusesTheWholeRuleInsteadOfBlockingMore() {
        val typo = """{"format":"focusflow.screenrules","version":1,"rules":[
            {"name":"Typo","packageName":"com.a","matchers":[{"viewIdSuffix":"tab","txt":"Reels"}]},
            {"name":"Fine","packageName":"com.b","matchers":[{"viewIdSuffix":"tab"}]}]}"""
        val result = CustomScreenRules.parseImport(typo, own)
        assertEquals(listOf("Fine"), result.accepted.map { it.name })
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].startsWith("Typo: Matcher 1 has an unknown field \"txt\""))
    }

    @Test
    fun aMatcherThatIsNotAnObjectRefusesTheRuleInsteadOfBeingDropped() {
        val text = """{"format":"focusflow.screenrules","version":1,"rules":[
            {"name":"Half","packageName":"com.a","matchers":[{"viewIdSuffix":"a"},"oops"]},
            {"name":"NotAList","packageName":"com.a","matchers":{"viewIdSuffix":"a"}}]}"""
        val result = CustomScreenRules.parseImport(text, own)
        assertTrue(result.accepted.isEmpty())
        assertEquals(2, result.rejected.size)
        assertTrue(result.rejected[0].contains("Matcher 2 is not an object"))
        assertTrue(result.rejected[1].contains("must be a list"))
    }

    @Test
    fun selectedOnlyMustBeABooleanSoATabRuleIsNeverSilentlyBroadened() {
        fun withSelected(value: String) =
            """[{"name":"R","packageName":"com.a","matchers":[{"descContains":"Reels","selectedOnly":$value}]}]"""
        assertTrue(CustomScreenRules.parseImport(withSelected("true"), own).accepted.single().matchers.single().selectedOnly)
        assertTrue(CustomScreenRules.parseImport(withSelected("\"true\""), own).accepted.single().matchers.single().selectedOnly)
        assertFalse(CustomScreenRules.parseImport(withSelected("false"), own).accepted.single().matchers.single().selectedOnly)
        for (bad in listOf("\"yes\"", "1", "\"\"", "[true]")) {
            val result = CustomScreenRules.parseImport(withSelected(bad), own)
            assertTrue(bad, result.accepted.isEmpty())
            assertTrue(bad, result.rejected.single().contains("selectedOnly must be true or false"))
        }
    }

    @Test
    fun extraFieldsOnTheRuleItselfAreIgnoredButExtraFieldsOnAMatcherAreNot() {
        val text = """[{"name":"R","packageName":"com.a","note":"made by AI","matchers":[{"viewIdSuffix":"a"}]}]"""
        assertEquals(1, CustomScreenRules.parseImport(text, own).accepted.size)
    }

    @Test
    fun exportIsReadableAndGoesBackInUnchanged() {
        val rules = listOf(
            rule(name = "Reels", matchers = listOf(NodeMatcher(descContains = "Reels", selectedOnly = true))),
            rule(name = "Shorts", packageName = "com.google.android.youtube", matchMode = MatchMode.ANY,
                matchers = listOf(NodeMatcher(viewIdSuffix = "shorts"), NodeMatcher(textContains = "Shorts"))),
        )
        val text = CustomScreenRules.exportEnvelope(rules)
        assertTrue(text.contains("\n"))
        val back = CustomScreenRules.parseImport(text, own)
        assertTrue(back.rejected.isEmpty())
        assertEquals(rules.map { it.copy(id = "", enabled = false) }, back.accepted.map { it.copy(id = "", enabled = false) })
    }

    @Test
    fun aFullExportOfMaximumSizeRulesStillFitsTheImportLimit() {
        val long = { tag: String, n: Int -> (tag + n).padEnd(CustomScreenRules.MAX_FIELD_LEN, 'x') }
        val rules = List(CustomScreenRules.MAX_RULES) { n ->
            rule(
                name = long("name", n),
                packageName = long("com.p", n),
                matchMode = MatchMode.ALL,
                matchers = List(CustomScreenRules.MAX_MATCHERS) { k ->
                    NodeMatcher(
                        viewIdSuffix = long("id$k-", n),
                        className = long("cls$k-", n),
                        textContains = long("text$k-", n),
                        descContains = long("desc$k-", n),
                        selectedOnly = true,
                    )
                },
            )
        }
        assertTrue(rules.all { CustomScreenRules.validate(it, own) == null })
        val text = CustomScreenRules.exportEnvelope(rules)
        assertTrue("export is ${text.length} characters", text.length <= CustomScreenRules.MAX_IMPORT_CHARS)
        val result = CustomScreenRules.parseImport(text, own)
        assertTrue(result.rejected.toString(), result.rejected.isEmpty())
        assertEquals(CustomScreenRules.MAX_RULES, result.accepted.size)
    }
}
