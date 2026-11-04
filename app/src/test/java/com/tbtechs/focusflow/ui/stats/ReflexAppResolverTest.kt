package com.tbtechs.focusflow.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReflexAppResolverTest {
    private val apps = listOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.maps" to "Google Maps",
        "com.google.android.apps.photos" to "Google Photos",
        "app.x" to "X",
    )

    @Test
    fun exactNameIgnoresCaseSpacesAndPunctuation() {
        assertEquals("com.google.android.youtube", ReflexAppResolver.resolve("you tube", apps))
        assertEquals("com.instagram.android", ReflexAppResolver.resolve("  INSTAGRAM ", apps))
        assertEquals("app.x", ReflexAppResolver.resolve("x", apps))
    }

    @Test
    fun partialNameIsAcceptedOnlyWhenItIsUnambiguous() {
        assertEquals("com.instagram.android", ReflexAppResolver.resolve("insta", apps))
        assertNull(ReflexAppResolver.resolve("google", apps))
    }

    @Test
    fun blankOrUnknownNamesResolveToNothing() {
        assertNull(ReflexAppResolver.resolve("", apps))
        assertNull(ReflexAppResolver.resolve("tiktok", apps))
        assertNull(ReflexAppResolver.resolve("in", apps))
    }
}
