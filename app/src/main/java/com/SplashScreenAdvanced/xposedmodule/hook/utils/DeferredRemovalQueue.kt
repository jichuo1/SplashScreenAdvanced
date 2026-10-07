package com.SplashScreenAdvanced.xposedmodule.hook.utils

import java.util.concurrent.atomic.AtomicBoolean

/** 重载只在同步调用和延迟移除均结束时放行；不取消已接管的宿主操作。 */
internal class DeferredRemovalQueue(private val capacity: Int = 128) {
    private val lock = Any()
    private val pending = mutableSetOf<Any>()
    private var activeCalls = 0
    private var accepting = true

    fun enterCall(): Boolean = synchronized(lock) {
        if (!accepting) false else {
            activeCalls++
            true
        }
    }

    fun exitCall() = synchronized(lock) { activeCalls-- }

    fun schedule(post: (Runnable) -> Boolean, action: () -> Unit): Boolean {
        val token = Any()
        synchronized(lock) {
            if (!accepting || pending.size >= capacity) return false
            pending += token
        }
        val claimed = AtomicBoolean(false)
        val work = Runnable {
            if (claimed.compareAndSet(false, true)) {
                try {
                    action()
                } finally {
                    synchronized(lock) { pending -= token }
                }
            }
        }
        val posted = try {
            post(work)
        } catch (_: Exception) {
            false
        }
        if (!posted) {
            // 拒绝排队后调用方会同步放行；失效 runnable 即使被错误投递也不会再执行。
            if (claimed.compareAndSet(false, true)) {
                synchronized(lock) { pending -= token }
                return false
            }
            // 极端情况下 post 已开始执行才报错，原操作仍由本队列持有。
            return true
        }
        return true
    }

    fun prepareReload(): Boolean = synchronized(lock) {
        if (activeCalls != 0 || pending.isNotEmpty()) false else {
            accepting = false
            true
        }
    }
}
