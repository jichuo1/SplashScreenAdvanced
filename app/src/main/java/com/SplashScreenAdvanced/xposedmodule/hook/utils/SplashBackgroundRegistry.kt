package com.SplashScreenAdvanced.xposedmodule.hook.utils

import java.util.WeakHashMap

/** A splash background outlives its synchronous build call; ownership follows the actual View. */
internal class SplashBackgroundRegistry {
    data class Background(val color: Int, val customApplied: Boolean)
    private val backgrounds = WeakHashMap<Any, Background>()

    @Synchronized fun record(view: Any, color: Int, customApplied: Boolean) {
        backgrounds[view] = Background(opaque(color), customApplied)
    }

    @Synchronized fun get(view: Any?): Background? = view?.let(backgrounds::get)
    @Synchronized fun remove(view: Any) { backgrounds.remove(view) }

    companion object {
        /** Splash colors have no opacity control in settings and must cover the application's content. */
        fun opaque(color: Int): Int = color or 0xff000000.toInt()
    }
}
