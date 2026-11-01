package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnceKeysTest {
    @Test
    fun repeatedKeyIsReportedOnlyOnce() {
        val keys = OnceKeys()
        assertTrue(keys.firstTime("a|x"))
        assertFalse(keys.firstTime("a|x"))
        assertTrue(keys.firstTime("a|y"))
    }

    @Test
    fun oldestKeyIsEvictedBeyondCapacity() {
        val keys = OnceKeys(capacity = 2)
        keys.firstTime("1")
        keys.firstTime("2")
        keys.firstTime("3")
        assertTrue(keys.firstTime("1"))
        assertFalse(keys.firstTime("3"))
    }
}
