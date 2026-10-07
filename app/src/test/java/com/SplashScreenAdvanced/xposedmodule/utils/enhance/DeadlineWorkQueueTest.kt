package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DeadlineWorkQueueTest {
    @Test
    fun expiredWorkIsNeverPosted() {
        val queue = DeadlineWorkQueue<String>({ 10L }, { fail("expired post"); true }, {}, {})
        assertNull(queue.run(10L) { fail("expired compute"); "value" })
    }

    @Test
    fun acceptedResultTransfersWithoutDisposal() {
        var disposed = 0
        val queue = DeadlineWorkQueue<String>({ 0L }, { it.run(); true }, {}, { disposed++ })
        assertEquals("value", queue.run(100L) { "value" })
        assertEquals(0, disposed)
    }

    @Test
    fun queuedTimeoutRemovesWorkAndDisarmsLateDelivery() {
        var queued: Runnable? = null
        var removed: Runnable? = null
        var calls = 0
        val now = AtomicLong(0L)
        val queue = DeadlineWorkQueue<String>(now::get, {
            queued = it
            now.set(100L)
            true
        }, { removed = it }, {})
        assertNull(queue.run(100L) { calls++; "value" })
        assertSame(queued, removed)
        queued!!.run()
        assertEquals(0, calls)
    }

    @Test
    fun runningTimeoutDisposesLateResultAndPreventsQueueGrowth() {
        val worker = Executors.newSingleThreadExecutor()
        val callers = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val clock = AtomicLong(0L)
        val posts = AtomicInteger()
        val queue = DeadlineWorkQueue<String>(clock::get, {
            posts.incrementAndGet()
            worker.execute(it)
            true
        }, {}, { disposed.countDown() })
        try {
            val result = callers.submit<String?> {
                queue.run(20L) {
                    started.countDown()
                    assertTrue(finish.await(2, TimeUnit.SECONDS))
                    "late bitmap"
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertNull(result.get(2, TimeUnit.SECONDS))
            assertNull(queue.run(100L) { fail("busy worker"); "other" })
            assertEquals(1, posts.get())
            finish.countDown()
            assertTrue(disposed.await(2, TimeUnit.SECONDS))
            assertEquals("next", queue.run(100L) { "next" })
        } finally {
            finish.countDown()
            worker.shutdownNow()
            callers.shutdownNow()
        }
    }

    @Test
    fun rejectedPostAllowsNextAttempt() {
        var accept = false
        val queue = DeadlineWorkQueue<String>({ 0L }, { if (accept) it.run(); accept }, {}, {})
        assertNull(queue.run(100L) { "rejected" })
        accept = true
        assertEquals("accepted", queue.run(100L) { "accepted" })
    }
}
