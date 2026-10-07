package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DeferredRemovalQueueTest {
    @Test
    fun reloadWaitsForRemovalWithoutCancellingOrDuplicatingIt() {
        val queue = DeferredRemovalQueue()
        val posted = mutableListOf<Runnable>()
        var calls = 0
        assertTrue(queue.schedule({ posted += it; true }) { calls++ })
        assertFalse(queue.prepareReload())
        posted.single().run()
        posted.single().run()
        assertEquals(1, calls)
        assertTrue(queue.prepareReload())
        assertFalse(queue.schedule({ fail("post after reload"); true }) { calls++ })
        assertFalse(queue.enterCall())
    }

    @Test
    fun synchronousCallBlocksReloadUntilItExits() {
        val queue = DeferredRemovalQueue()
        assertTrue(queue.enterCall())
        assertFalse(queue.prepareReload())
        assertTrue(queue.enterCall())
        queue.exitCall()
        assertFalse(queue.prepareReload())
        queue.exitCall()
        assertTrue(queue.prepareReload())
    }

    @Test
    fun rejectedPostLeavesOriginalCallToCallerAndDisarmsLateRunnable() {
        val queue = DeferredRemovalQueue()
        var late: Runnable? = null
        var calls = 0
        assertFalse(queue.schedule({ late = it; false }) { calls++ })
        calls++ // 模拟调用方同步执行原方法。
        late!!.run()
        assertEquals(1, calls)
        assertTrue(queue.prepareReload())
    }

    @Test
    fun throwingPostDisarmsLateDelivery() {
        val queue = DeferredRemovalQueue()
        var late: Runnable? = null
        var calls = 0
        assertFalse(queue.schedule({ late = it; throw IllegalStateException("post failed") }) { calls++ })
        calls++
        late!!.run()
        assertEquals(1, calls)
        assertTrue(queue.prepareReload())
    }

    @Test
    fun postFailureAfterExecutionDoesNotRequestDuplicateOriginalCall() {
        val queue = DeferredRemovalQueue()
        var calls = 0
        assertTrue(queue.schedule({ it.run(); false }) { calls++ })
        assertEquals(1, calls)
        assertTrue(queue.prepareReload())
    }

    @Test
    fun runningRemovalStillBlocksReloadAndReleasesAfterFailure() {
        val queue = DeferredRemovalQueue()
        var work: Runnable? = null
        assertTrue(queue.schedule({ work = it; true }) {
            assertFalse(queue.prepareReload())
            throw IllegalStateException("original failed")
        })
        try {
            work!!.run()
            fail("expected original failure")
        } catch (_: IllegalStateException) {
            assertTrue(queue.prepareReload())
        }
    }

    @Test
    fun saturatedQueueLetsCallerUseOriginal() {
        val queue = DeferredRemovalQueue(capacity = 1)
        var first: Runnable? = null
        assertTrue(queue.schedule({ first = it; true }) {})
        assertFalse(queue.schedule({ fail("over capacity"); true }) {})
        first!!.run()
        assertTrue(queue.prepareReload())
    }

    @Test
    fun simultaneousExecutionClaimsOriginalOnce() {
        val queue = DeferredRemovalQueue()
        var work: Runnable? = null
        val calls = AtomicInteger()
        val gate = CountDownLatch(1)
        assertTrue(queue.schedule({ work = it; true }) { calls.incrementAndGet() })
        val threads = List(8) { Thread { gate.await(); work!!.run() }.apply { start() } }
        gate.countDown()
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(2)); assertFalse(it.isAlive) }
        assertEquals(1, calls.get())
        assertTrue(queue.prepareReload())
    }
}
