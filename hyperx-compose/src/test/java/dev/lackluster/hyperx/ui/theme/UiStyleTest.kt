package dev.lackluster.hyperx.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStyleTest {
    @Test
    fun fromPrefMapsKnownValuesAndFallsBackToMiuix() {
        assertEquals(UiStyle.Miuix, UiStyle.fromPref(0))
        assertEquals(UiStyle.MaterialYou, UiStyle.fromPref(1))
        assertEquals(UiStyle.Miuix, UiStyle.fromPref(-1))
        assertEquals(UiStyle.Miuix, UiStyle.fromPref(99))
    }

    @Test
    fun styleFlagsMatchEnum() {
        assertTrue(UiStyle.MaterialYou.isMaterialYou)
        assertFalse(UiStyle.MaterialYou.isMiuix)
        assertTrue(UiStyle.Miuix.isMiuix)
        assertFalse(UiStyle.Miuix.isMaterialYou)
    }
}
