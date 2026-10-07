package com.SplashScreenAdvanced.xposedmodule.hook.utils

/** 同步 Hook 的嵌套会话；退出内层调用后恢复外层，线程复用时不残留状态。 */
internal class HookCallScope<T : Any> {
    private val frames = ThreadLocal<ArrayDeque<T>>()

    val current: T? get() = frames.get()?.lastOrNull()

    fun enter(value: T) {
        val stack = frames.get() ?: ArrayDeque<T>().also { frames.set(it) }
        stack.addLast(value)
    }

    fun exit() {
        val stack = frames.get() ?: return
        stack.removeLastOrNull()
        if (stack.isEmpty()) frames.remove()
    }
}
