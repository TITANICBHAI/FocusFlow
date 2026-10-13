package com.tbtechs.focusflow.ui.navigation

import com.tbtechs.focusflow.data.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTextScaleContextTest {
    private val settings = AppSettings(
        generalTextScale = 1.1f,
        homeTextScale = 1.6f,
        focusTextScale = 1.2f,
        statsTextScale = 1.3f,
        settingsTextScale = 1.4f,
        defenseTextScale = 1.5f,
    )

    @Test
    fun everySharedRouteUsesItsOpeningTabAndDirectEntriesUseGeneral() {
        val sharedRoutes = listOf(
            Routes.ACTIVE,
            Routes.HOW_TO_USE,
            Routes.PERMISSIONS,
        )
        val tabScales = listOf(
            Routes.HOME to 1.6f,
            Routes.FOCUS to 1.2f,
            Routes.STATS to 1.3f,
            Routes.SETTINGS to 1.4f,
            Routes.DEFENSE to 1.5f,
        )

        sharedRoutes.forEach { route ->
            tabScales.forEach { (tab, expectedScale) ->
                assertEquals(
                    "$route follows $tab",
                    expectedScale,
                    RouteTextScaleContext.scaleFor(route, tab, settings),
                    0f,
                )
            }
            assertEquals(
                "$route direct entry uses General",
                1.1f,
                RouteTextScaleContext.scaleFor(route, null, settings),
                0f,
            )
        }
    }

    @Test
    fun tabOwnedDestinationsUseOwnerWhenReachedInternallyAndGeneralWhenDirect() {
        val ownerRoutes = listOf(
            Routes.TEXT_SIZE_SETTINGS to Routes.SETTINGS,
            Routes.SETTINGS_HOW_TO_USE to Routes.SETTINGS,
            Routes.FOCUSFLOW_FILE_GUIDE to Routes.SETTINGS,
            Routes.USER_PROFILE to Routes.SETTINGS,
            Routes.CHANGELOG to Routes.SETTINGS,
            Routes.PRIVACY_POLICY to Routes.SETTINGS,
            Routes.TERMS_OF_SERVICE to Routes.SETTINGS,
            Routes.REPORTS to Routes.STATS,
            Routes.REPORT to Routes.STATS,
            Routes.ALWAYS_ON to Routes.DEFENSE,
            Routes.BLOCK_DEFENSE to Routes.DEFENSE,
            Routes.HOME_LAUNCHER_SETUP to Routes.DEFENSE,
            Routes.KEYWORD_BLOCKER to Routes.DEFENSE,
            Routes.PASSWORD_PROTECTION to Routes.DEFENSE,
            Routes.VPN_BLOCK_LIST to Routes.DEFENSE,
        )

        ownerRoutes.forEach { (route, ownerTab) ->
            val expectedScale = when (ownerTab) {
                Routes.SETTINGS -> 1.4f
                Routes.STATS -> 1.3f
                else -> 1.5f
            }
            assertEquals(
                "$route uses its owner tab when reached internally",
                expectedScale,
                RouteTextScaleContext.scaleFor(route, Routes.HOME, settings),
                0f,
            )
            assertEquals(
                "$route direct entry uses General",
                1.1f,
                RouteTextScaleContext.scaleFor(route, null, settings),
                0f,
            )
        }
    }

    @Test
    fun homeAndHomeOriginSharedNavigationUseTheScheduleOverride() {
        assertEquals(1.6f, RouteTextScaleContext.scaleFor(Routes.HOME, Routes.SETTINGS, settings), 0f)
        assertEquals(
            Routes.HOME,
            RouteTextScaleContext.sourceTabForDestination(Routes.HOME, null, Routes.ACTIVE),
        )
        assertEquals(
            Routes.HOME,
            RouteTextScaleContext.sourceTabForDestination(Routes.ACTIVE, Routes.HOME, Routes.PERMISSIONS),
        )
    }

    @Test
    fun screenOverrideWinsForItsTabAndDirectEntryStillUsesGeneral() {
        val reportScaleKey = RouteTextScaleContext.screenScaleKey(Routes.STATS, Routes.REPORTS)
        val configured = settings.copy(
            screenTextScales = mapOf(reportScaleKey to 0.87f),
        )

        assertEquals(
            0.87f,
            RouteTextScaleContext.scaleFor(Routes.REPORTS, Routes.STATS, configured),
            0f,
        )
        assertEquals(
            1.3f,
            RouteTextScaleContext.scaleFor(Routes.REPORTS, Routes.HOME, configured),
            0f,
        )
        assertEquals(
            1.1f,
            RouteTextScaleContext.scaleFor(Routes.REPORTS, null, configured),
            0f,
        )
    }

    @Test
    fun focusSubscreenOverridesAreIndependentAndFallBackToFocusScale() {
        val standaloneKey = RouteTextScaleContext.screenScaleKey(
            Routes.FOCUS,
            RouteTextScaleContext.FOCUS_STANDALONE_SETUP_SCREEN,
        )
        val configured = settings.copy(screenTextScales = mapOf(standaloneKey to 0.9f))

        assertEquals(
            0.9f,
            RouteTextScaleContext.screenScaleFor(
                Routes.FOCUS,
                RouteTextScaleContext.FOCUS_STANDALONE_SETUP_SCREEN,
                configured,
                inheritedScale = 1.2f,
            ),
            0f,
        )
        assertEquals(
            1.2f,
            RouteTextScaleContext.screenScaleFor(
                Routes.FOCUS,
                RouteTextScaleContext.FOCUS_STANDALONE_PANEL_SCREEN,
                configured,
                inheritedScale = 1.2f,
            ),
            0f,
        )
    }

    @Test
    fun navigationCarriesSharedSourceAndSwitchesToOwnedTabForOwnedDestinations() {
        val activeCallers = listOf(
            Routes.HOME,
            Routes.FOCUS,
            Routes.STATS,
            Routes.SETTINGS,
            Routes.DEFENSE,
        )
        activeCallers.forEach { caller ->
            assertEquals(
                "$caller remains the source of ACTIVE",
                caller,
                RouteTextScaleContext.sourceTabForDestination(caller, null, Routes.ACTIVE),
            )
        }
        listOf(Routes.FOCUS, Routes.SETTINGS, Routes.DEFENSE).forEach { caller ->
            assertEquals(
                "$caller remains the source of PERMISSIONS",
                caller,
                RouteTextScaleContext.sourceTabForDestination(caller, null, Routes.PERMISSIONS),
            )
        }
        activeCallers.forEach { caller ->
            assertEquals(
                "$caller remains the source of HOW_TO_USE",
                caller,
                RouteTextScaleContext.sourceTabForDestination(caller, null, Routes.HOW_TO_USE),
            )
        }
        assertEquals(
            Routes.DEFENSE,
            RouteTextScaleContext.sourceTabForDestination(Routes.ACTIVE, Routes.HOME, Routes.ALWAYS_ON),
        )
        assertEquals(
            Routes.DEFENSE,
            RouteTextScaleContext.sourceTabForDestination(Routes.STATS, null, Routes.BLOCK_DEFENSE),
        )
        assertNull(
            RouteTextScaleContext.sourceTabForDestination(Routes.ACTIVE, null, Routes.PERMISSIONS),
        )
        assertNull(
            RouteTextScaleContext.sourceTabForDestination(Routes.NOT_FOUND, null, Routes.ALWAYS_ON),
        )
    }

    @Test
    fun sourceTabIsAddedWithoutDroppingExistingRouteArguments() {
        assertEquals(
            "${Routes.PRIVACY_POLICY}?revisit=true&sourceTab=${Routes.SETTINGS}",
            RouteTextScaleContext.routeWithSourceTab(
                "${Routes.PRIVACY_POLICY}?revisit=true",
                Routes.SETTINGS,
            ),
        )
        assertEquals(Routes.ACTIVE, RouteTextScaleContext.routeWithSourceTab(Routes.ACTIVE, null))
    }
}