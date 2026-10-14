package com.tbtechs.focusflow.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualPackageNameTest {
    @Test
    fun trimsAndNormalizesValidPackageNames() {
        assertEquals("com.example.my_app", normalizeManualPackageName("  Com.Example.My_App "))
    }

    @Test
    fun rejectsIncompleteOrMalformedPackageNames() {
        listOf("", "example", "com.", ".example.app", "com.example.app!", "com..example")
            .forEach { value ->
                assertNull("$value should be rejected", normalizeManualPackageName(value))
            }
    }
}
