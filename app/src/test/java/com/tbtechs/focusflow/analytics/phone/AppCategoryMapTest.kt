package com.tbtechs.focusflow.analytics.phone

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryMapTest {

    private fun cat(pkg: String, api: Int? = null, game: Boolean = false) =
        AppCategoryMap.categorize(pkg, api, game)

    @Test
    fun overrideBeatsApiCategory() {
        assertEquals(UsageCategory.SOCIAL, cat("com.instagram.android", ApplicationInfo.CATEGORY_VIDEO))
    }

    @Test
    fun overrideBeatsGameFlag() {
        assertEquals(UsageCategory.COMMUNICATION, cat("com.whatsapp", game = true))
    }

    @Test
    fun eachApiCategoryMaps() {
        assertEquals(UsageCategory.SOCIAL, cat("com.zz.a", ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(UsageCategory.ENTERTAINMENT, cat("com.zz.a", ApplicationInfo.CATEGORY_VIDEO))
        assertEquals(UsageCategory.ENTERTAINMENT, cat("com.zz.a", ApplicationInfo.CATEGORY_AUDIO))
        assertEquals(UsageCategory.GAMES, cat("com.zz.a", ApplicationInfo.CATEGORY_GAME))
        assertEquals(UsageCategory.PRODUCTIVITY, cat("com.zz.a", ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertEquals(UsageCategory.NEWS_READING, cat("com.zz.a", ApplicationInfo.CATEGORY_NEWS))
        assertEquals(UsageCategory.TOOLS, cat("com.zz.a", ApplicationInfo.CATEGORY_MAPS))
        assertEquals(UsageCategory.TOOLS, cat("com.zz.a", ApplicationInfo.CATEGORY_IMAGE))
    }

    @Test
    fun undefinedApiCategoryFallsThrough() {
        assertEquals(UsageCategory.OTHER, cat("com.zz.a", ApplicationInfo.CATEGORY_UNDEFINED))
        assertEquals(UsageCategory.GAMES, cat("com.zz.a", ApplicationInfo.CATEGORY_UNDEFINED, game = true))
        assertEquals(UsageCategory.EDUCATION, cat("com.zz.learn", ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test
    fun gameFlagMapsToGames() {
        assertEquals(UsageCategory.GAMES, cat("com.zz.a", game = true))
    }

    @Test
    fun apiCategoryBeatsGameFlag() {
        assertEquals(UsageCategory.SOCIAL, cat("com.zz.a", ApplicationInfo.CATEGORY_SOCIAL, game = true))
    }

    @Test
    fun apiCategoryBeatsKeywords() {
        assertEquals(UsageCategory.PRODUCTIVITY, cat("com.zz.game", ApplicationInfo.CATEGORY_PRODUCTIVITY))
    }

    @Test
    fun keywordFallbackHits() {
        assertEquals(UsageCategory.EDUCATION, cat("com.foo.learnenglish"))
        assertEquals(UsageCategory.GAMES, cat("com.example.game"))
        assertEquals(UsageCategory.GAMES, cat("com.example.games.arcade"))
        assertEquals(UsageCategory.GAMES, cat("com.example.casino_royale"))
        assertEquals(UsageCategory.SOCIAL, cat("com.example.socialhub"))
        assertEquals(UsageCategory.COMMUNICATION, cat("com.example.chat"))
        assertEquals(UsageCategory.ENTERTAINMENT, cat("com.example.tv"))
        assertEquals(UsageCategory.NEWS_READING, cat("com.example.news"))
        assertEquals(UsageCategory.NEWS_READING, cat("com.example.readerapp"))
        assertEquals(UsageCategory.TOOLS, cat("com.example.calculatorpro"))
    }

    @Test
    fun shortKeywordsNeedAWholeSegment() {
        assertEquals(UsageCategory.OTHER, cat("com.stvision.app"))
        assertEquals(UsageCategory.OTHER, cat("com.example.tvguide"))
        assertEquals(UsageCategory.OTHER, cat("com.example.smsx"))
        assertEquals(UsageCategory.OTHER, cat("com.example.educator"))
        assertEquals(UsageCategory.OTHER, cat("com.example.newsletter"))
    }

    /**
     * Plan ruling: the plan's example `com.example.mathgame -> GAMES` contradicts the plan's own rule
     * ("game" has fewer than 5 letters, so it only matches a whole segment). The rule text wins.
     */
    @Test
    fun mathgameIsNotMatchedByTheGameKeywordRule() {
        assertEquals(UsageCategory.OTHER, cat("com.example.mathgame"))
    }

    @Test
    fun keywordMatchingIsCaseInsensitiveAndSplitsOnUnderscore() {
        assertEquals(UsageCategory.GAMES, cat("Com.Example.GAME"))
        assertEquals(UsageCategory.EDUCATION, cat("com.example.my_study_app"))
    }

    @Test
    fun keywordGroupOrderIsGamesFirst() {
        assertEquals(UsageCategory.GAMES, cat("com.example.chat.game"))
        assertEquals(UsageCategory.EDUCATION, cat("com.example.learn.social"))
        assertEquals(UsageCategory.SOCIAL, cat("com.example.social.chat"))
        assertEquals(UsageCategory.COMMUNICATION, cat("com.example.chat.video"))
    }

    @Test
    fun unknownPackageIsOther() {
        assertEquals(UsageCategory.OTHER, cat("com.totally.unknown"))
        assertEquals(UsageCategory.OTHER, cat(""))
    }

    @Test
    fun prefixRulesApplyAfterExactTable() {
        assertEquals(UsageCategory.GAMES, cat("com.supercell.clashofclans"))
        assertEquals(UsageCategory.GAMES, cat("com.king.candycrushsaga"))
        assertEquals(UsageCategory.GAMES, cat("com.miniclip.eightballpool"))
        assertEquals(UsageCategory.GAMES, cat("com.gameloft.android.ANMP"))
        assertEquals(UsageCategory.GAMES, cat("com.ea.game.fifa"))
        assertEquals(UsageCategory.PRODUCTIVITY, cat("com.microsoft.office.word"))
        assertEquals(UsageCategory.PRODUCTIVITY, cat("com.google.android.apps.docs.editors.sheets"))
        // Exact entry wins over a prefix that would also match.
        assertEquals(UsageCategory.COMMUNICATION, cat("com.microsoft.office.outlook"))
    }

    @Test
    fun prefixRulesDoNotMatchPartialSegments() {
        assertEquals(UsageCategory.OTHER, cat("com.kingdom.app"))
        assertEquals(UsageCategory.OTHER, cat("com.supercellular.app"))
    }

    @Test
    fun browsersAreTools() {
        assertEquals(UsageCategory.TOOLS, cat("com.android.chrome"))
    }

    @Test
    fun overrideTableHasNoDuplicatePackages() {
        val all = AppCategoryMap.EXACT_OVERRIDES_BY_CATEGORY.values.flatten()
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun overrideTableCountsMatchAppendixB() {
        val counts = AppCategoryMap.EXACT_OVERRIDES_BY_CATEGORY.mapValues { it.value.size }
        assertEquals(20, counts[UsageCategory.SOCIAL])
        assertEquals(16, counts[UsageCategory.COMMUNICATION])
        assertEquals(26, counts[UsageCategory.ENTERTAINMENT])
        assertEquals(15, counts[UsageCategory.GAMES])
        assertEquals(14, counts[UsageCategory.EDUCATION])
        assertEquals(20, counts[UsageCategory.PRODUCTIVITY])
        assertEquals(9, counts[UsageCategory.NEWS_READING])
        assertEquals(21, counts[UsageCategory.TOOLS])
        assertEquals(141, counts.values.sum())
    }

    @Test
    fun everyOverrideResolvesToItsOwnCategoryEvenWithConflictingApiInput() {
        for ((category, packages) in AppCategoryMap.EXACT_OVERRIDES_BY_CATEGORY) {
            for (pkg in packages) {
                assertEquals(pkg, category, cat(pkg, ApplicationInfo.CATEGORY_MAPS))
            }
        }
    }

    @Test
    fun distractionFlagOnlyForSocialEntertainmentGames() {
        val distracting = UsageCategory.values().filter { it.isDistraction }.toSet()
        assertEquals(setOf(UsageCategory.SOCIAL, UsageCategory.ENTERTAINMENT, UsageCategory.GAMES), distracting)
        assertTrue(UsageCategory.GAMES.isDistraction)
        assertFalse(UsageCategory.OTHER.isDistraction)
    }
}
