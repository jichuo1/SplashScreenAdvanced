package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 单个物理工作槽；调用方超时后释放过期结果，运行中的工作结束前不再排新任务。 */
internal class DeadlineWorkQueue<T : Any>(
    private val clock: () -> Long,
    private val post: (Runnable) -> Boolean,
    private val remove: (Runnable) -> Unit,
    private val dispose: (T) -> Unit,
) {
    private class Job<T> {
        val ready = CountDownLatch(1)
        var started = false
        var cancelled = false
        var value: T? = null
    }

    private val lock = Any()
    private var current: Job<T>? = null

    fun run(deadline: Long, compute: () -> T?): T? {
        if (clock() >= deadline) return null
        val job = Job<T>()
        synchronized(lock) {
            if (current != null) return null
            current = job
        }
        val work = Runnable {
            synchronized(lock) {
                if (job.cancelled) return@Runnable
                job.started = true
            }
            var result: T? = null
            try {
                if (clock() < deadline) result = compute()
            } finally {
                val discarded = synchronized(lock) {
                    val expired = job.cancelled || clock() >= deadline
                    if (!expired) job.value = result
                    if (current === job) current = null
                    job.ready.countDown()
                    if (expired) result else null
                }
                discarded?.let(dispose)
            }
        }
        val posted = try { post(work) } catch (_: Exception) { false }
        try {
            if (posted) job.ready.await((deadline - clock()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val result = synchronized(lock) {
            job.cancelled = true
            if (!job.started && current === job) current = null
            val value = job.value
            job.value = null
            value
        }
        remove(work)
        if (clock() >= deadline || Thread.currentThread().isInterrupted || !posted) {
            result?.let(dispose)
            return null
        }
        return result
    }
}
