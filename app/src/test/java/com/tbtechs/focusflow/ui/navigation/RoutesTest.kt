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
}
