package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeRuleTest {
    private fun isExcept(
        pkg: String,
        enabled: Boolean = true,
        exceptionMode: Boolean = false,
        scope: Set<String> = setOf("tv.danmaku.bilix"),
        forceShow: Set<String> = emptySet(),
    ) = ScopeRule.isExcept(pkg, enabled, exceptionMode, scope, forceShow)

    @Test
    fun blankPackageIsAlwaysExcepted() {
        assertTrue(isExcept("", enabled = false))
        assertTrue(isExcept("  "))
    }

    @Test
    fun disabledCustomScopeNeverExcepts() {
        assertFalse(isExcept("com.github.android", enabled = false))
    }

    @Test
    fun selectedOnlyModeExceptsUnlistedApps() {
        assertTrue(isExcept("com.github.android"))
        assertFalse(isExcept("tv.danmaku.bilix"))
    }

    @Test
    fun forceShowAppIsInScopeInSelectedOnlyMode() {
        assertFalse(isExcept("com.github.android", forceShow = setOf("com.github.android")))
        // 强制显示列表里没有的应用不受影响
        assertTrue(isExcept("com.other", forceShow = setOf("com.github.android")))
    }

    @Test
    fun exceptionModeExclusionWinsOverForceShow() {
        val scope = setOf("com.github.android")
        assertTrue(isExcept("com.github.android", exceptionMode = true, scope = scope, forceShow = scope))
        assertFalse(isExcept("com.other", exceptionMode = true, scope = scope, forceShow = scope))
    }
}
