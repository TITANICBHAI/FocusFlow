package com.tbtechs.focusflow.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    @Test
    fun internalAndStatefulRoutesAreRejectedFromExternalPaths() {
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("onboarding"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("active"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("guarded_adjustments"))
        assertFalse(Routes.TEXT_SIZE_SETTINGS in Routes.externalLinkableRoutes)
        assertFalse(Routes.SETTINGS_HOW_TO_USE in Routes.externalLinkableRoutes)
        assertTrue(Routes.SETTINGS_USAGE_DATA in Routes.architectureRoutes)
        assertFalse(Routes.SETTINGS_USAGE_DATA in Routes.externalLinkableRoutes)
        assertTrue(Routes.FOCUSFLOW_FILE_GUIDE in Routes.architectureRoutes)
        assertFalse(Routes.FOCUSFLOW_FILE_GUIDE in Routes.externalLinkableRoutes)
    }

    @Test
    fun knownPublicDeepLinksStillResolve() {
        assertEquals(Routes.PRIVACY_POLICY, Routes.fromPath("/privacy-policy?source=test"))
        assertEquals(Routes.ALWAYS_ON, Routes.fromPath("always-on?package=com.example.app"))
        assertEquals(Routes.ALWAYS_ON, Routes.fromPath("always-on?sourceTab=stats"))
        assertEquals(Routes.FOCUS, Routes.fromPath("focus"))
    }

    @Test
    fun permissionsDeepLinkResolvesToThePermissionsScreen() {
        assertEquals(Routes.PERMISSIONS, Routes.fromPath("/permissions"))
        assertTrue(Routes.PERMISSIONS in Routes.externalLinkableRoutes)
    }

    @Test
    fun importConfirmationIsInternalAndPartOfTheArchitectureRoutes() {
        assertTrue(Routes.IMPORT_CONFIRM in Routes.architectureRoutes)
        assertFalse(Routes.IMPORT_CONFIRM in Routes.externalLinkableRoutes)
    }

    @Test
    fun ownNotificationLinksOpenHomeInsteadOfNotFound() {
        assertEquals(Routes.HOME, Routes.fromLink("focusflow-internal", "live-task", "/3f2a9c"))
        assertEquals(Routes.HOME, Routes.fromLink("focusflow-internal", "reminder", "/slot-1"))
        assertEquals(Routes.HOME, Routes.fromLink(null, null, null))
    }

    @Test
    fun widgetAndAppLinksResolveByPathOrHost() {
        assertEquals(Routes.FOCUS, Routes.fromLink("focusflow", "app", "/focus"))
        assertEquals(Routes.FOCUS, Routes.fromLink("focusflow", "focus", null))
        assertEquals(Routes.FOCUS, Routes.fromLink("focusflow", null, "/focus"))
        assertEquals(Routes.PERMISSIONS, Routes.fromLink("focusflow", "app", "/permissions"))
        assertEquals(Routes.HOME, Routes.fromLink("focusflow", "app", "/"))
        assertEquals(Routes.NOT_FOUND, Routes.fromLink("focusflow", "app", "/nope"))
    }

    @Test
    fun customScreenRuleRoutesAreInternalOnly() {
        val routes = listOf(
            Routes.CUSTOM_RULES,
            Routes.CUSTOM_RULE_EDIT,
            Routes.CUSTOM_RULE_CAPTURE,
            Routes.CUSTOM_RULE_PICK,
            Routes.CUSTOM_RULES_TRANSFER,
        )
        routes.forEach { route ->
            assertTrue(route, route in Routes.architectureRoutes)
            assertFalse(route, route in Routes.externalLinkableRoutes)
            assertEquals(route, Routes.NOT_FOUND, Routes.fromPath(route))
        }
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("custom_rules"))
    }
}
