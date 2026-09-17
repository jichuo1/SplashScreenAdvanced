package dev.lackluster.hyperx.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey
import dev.lackluster.hyperx.ui.animation.PageMotionController

class Navigator(
    val backStack: MutableList<NavKey>,
    private val motion: PageMotionController? = null,
) {
    fun push(key: NavKey) {
        motion?.onNavigateForward(key)
        backStack.add(key)
    }

    fun replace(key: NavKey) {
        motion?.resetAfterImmediatePop()
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size <= 1) return
        val key = backStack.last()
        if (motion?.onNavigateBack(key) == true) return
        backStack.removeLastOrNull()
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        motion?.resetAfterImmediatePop()
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current() = backStack.lastOrNull()

    fun backStackSize() = backStack.size
}

val LocalNavigator = compositionLocalOf<Navigator> { error("Navigator not provided") }