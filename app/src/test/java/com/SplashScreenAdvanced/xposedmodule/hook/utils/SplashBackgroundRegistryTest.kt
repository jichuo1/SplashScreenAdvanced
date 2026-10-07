package com.SplashScreenAdvanced.xposedmodule.hook.utils

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SplashBackgroundRegistryTest {
    @Test fun asynchronousBackgroundKeepsItsOwnerAfterTheBuildSessionEnds() {
        val registry = SplashBackgroundRegistry()
        val calls = HookCallScope<String>()
        val view = Any()
        calls.enter("com.android.mms")
        registry.record(view, 0xfffafafa.toInt(), customApplied = false)
        calls.exit()
        assertNull(calls.current)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val background = worker.submit<SplashBackgroundRegistry.Background?> { registry.get(view) }.get(2, TimeUnit.SECONDS)
            assertEquals(0xfffafafa.toInt(), background?.color)
            assertEquals(false, background?.customApplied)
        } finally { worker.shutdownNow() }
    }

    @Test fun parallelApplicationsCannotTakeEachOthersCustomColor() {
        val registry = SplashBackgroundRegistry()
        val first = Any(); val second = Any()
        registry.record(first, 0xff112233.toInt(), customApplied = true)
        registry.record(second, 0xffeeeeee.toInt(), customApplied = false)
        assertEquals(0xff112233.toInt(), registry.get(first)?.color)
        assertEquals(true, registry.get(first)?.customApplied)
        assertEquals(0xffeeeeee.toInt(), registry.get(second)?.color)
        assertEquals(false, registry.get(second)?.customApplied)
    }

    @Test fun unknownOrExcludedViewsKeepTheOriginalOemPath() {
        val registry = SplashBackgroundRegistry()
        registry.record(Any(), 0xff112233.toInt(), customApplied = true)
        assertNull(registry.get(Any()))
        assertNull(registry.get(null))
    }

    @Test fun detachRemovesOnlyTheDetachedSplash() {
        val registry = SplashBackgroundRegistry()
        val first = Any(); val second = Any()
        registry.record(first, 0xff112233.toInt(), true)
        registry.record(second, 0xff445566.toInt(), false)
        registry.remove(first)
        assertNull(registry.get(first))
        assertEquals(0xff445566.toInt(), registry.get(second)?.color)
    }

    @Test fun transparentAndPartialAlphaColorsKeepTheirRgbWhileCoveringTheWindow() {
        val registry = SplashBackgroundRegistry()
        for (alpha in listOf(0, 1, 127, 254, 255)) {
            val view = Any()
            registry.record(view, (alpha shl 24) or 0x123456, false)
            assertEquals(0xff123456.toInt(), registry.get(view)?.color)
        }
        assertEquals(0xff000000.toInt(), SplashBackgroundRegistry.opaque(0))
    }

    @Test fun failedCustomApplicationDoesNotBlockTheSystemBackground() {
        val registry = SplashBackgroundRegistry()
        val view = Any()
        registry.record(view, 0xffeeeeee.toInt(), customApplied = false)
        assertFalse(checkNotNull(registry.get(view)).customApplied)
    }
}
