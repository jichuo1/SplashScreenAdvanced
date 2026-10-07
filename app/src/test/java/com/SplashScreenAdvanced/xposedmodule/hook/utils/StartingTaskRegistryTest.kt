package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartingTaskRegistryTest {
    @Test
    fun overlappingApplicationsKeepTheirOwnDurationAndIdentity() {
        val registry = StartingTaskRegistry<Pair<String, Long>>({ 100L })
        registry.put(1, "app A" to 1000L)
        registry.put(2, "app B" to 2000L)
        assertEquals("app A" to 1000L, registry.take(1))
        assertEquals("app B" to 2000L, registry.take(2))
        assertNull(registry.take(1))
    }

    @Test
    fun unknownRemovalDoesNotConsumeLatestApplication() {
        val registry = StartingTaskRegistry<String>({ 0L })
        registry.put(1, "app A")
        registry.put(null, "unknown")
        registry.put(-1, "invalid")
        assertNull(registry.take(null))
        assertNull(registry.take(2))
        assertEquals("app A", registry.take(1))
    }

    @Test
    fun expiredAndOverflowEntriesFailOpen() {
        var now = 0L
        val registry = StartingTaskRegistry<String>({ now }, timeoutMs = 100L, capacity = 2)
        registry.put(1, "old")
        now = 100L
        assertNull(registry.take(1))
        registry.put(2, "second")
        registry.put(3, "third")
        registry.put(4, "fourth")
        assertNull(registry.take(2))
        assertEquals("third", registry.take(3))
        assertEquals("fourth", registry.take(4))
    }

    @Test
    fun reusedTaskIdUsesNewestLaunch() {
        val registry = StartingTaskRegistry<String>({ 0L })
        registry.put(1, "old launch")
        registry.put(1, "new launch")
        assertEquals("new launch", registry.take(1))
    }
}
