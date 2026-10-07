package com.SplashScreenAdvanced.xposedmodule.hook.utils

/** 只按已验证的 taskId 关联创建/移除；未知签名不猜测最近一次启动的包名。 */
internal class StartingTaskRegistry<T : Any>(
    private val clock: () -> Long,
    private val timeoutMs: Long = 60_000L,
    private val capacity: Int = 128,
) {
    private data class Entry<T>(val value: T, val createdAt: Long)
    private val entries = LinkedHashMap<Int, Entry<T>>()

    @Synchronized
    fun put(taskId: Int?, value: T) {
        if (taskId == null || taskId < 0) return
        prune()
        entries.remove(taskId)
        entries[taskId] = Entry(value, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun take(taskId: Int?): T? {
        prune()
        return entries.remove(taskId)?.value
    }

    private fun prune() {
        val now = clock()
        entries.values.removeAll { now - it.createdAt >= timeoutMs }
    }
}
