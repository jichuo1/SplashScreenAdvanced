package com.gswxxn.restoresplashscreen.wrapper

import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.makeAccessible
import com.highcapable.kavaref.extension.toClassOrNull
import java.lang.reflect.Method

/**
 * 用于 HyperOS 的无描边 AdaptiveIconDrawable
 */
class NoStrokeAdaptiveIconDrawable private constructor(
    background: Drawable?,
    foreground: Drawable?,
    monochrome: Drawable?
) : AdaptiveIconDrawable(background, foreground, monochrome) {

    override fun draw(canvas: Canvas) {
        val getter = getIsIconStroke
        val setter = setIsIconStroke
        if (getter == null || setter == null) {
            super.draw(canvas)
            return
        }
        // IconCustomizer.setIsIconStroke 改的是 miui framework 里的**进程级全局开关**。
        // 多个线程同时绘制自适应图标时,"读旧值 -> 关 -> 绘制 -> 还原"三步会相互穿插:
        // 后进来的线程可能把已被改成 false 的值当成 previous 存下来, 还原后描边就被永久关掉了。
        // 这里把这段串行化——启动遮罩图标的绘制频次很低, 排队代价可以接受
        synchronized(strokeToggleLock) {
            val previous = runCatching { getter.invoke(null) as Boolean }.getOrDefault(true)
            try {
                runCatching { setter.invoke(null, false) }
                drawSuper(canvas)
            } finally {
                runCatching { setter.invoke(null, previous) }
            }
        }
    }

    /** super.draw 的转发, 避免在 lambda 里直接写 super 调用 */
    private fun drawSuper(canvas: Canvas) = super.draw(canvas)

    companion object {
        /** 保护 IconCustomizer 全局描边开关的「读-改-写-还原」序列 */
        private val strokeToggleLock = Any()

        private val iconCustomizerClass by lazy {
            "miui.content.res.IconCustomizer".toClassOrNull()
        }
        private val getIsIconStroke: Method? by lazy {
            runCatching {
                iconCustomizerClass
                    ?.getDeclaredMethod("getIsIconStroke")
                    ?.apply { makeAccessible() }
            }.getOrNull()
        }
        private val setIsIconStroke: Method? by lazy {
            runCatching {
                iconCustomizerClass
                    ?.getDeclaredMethod("setIsIconStroke", classOf<Boolean>())
                    ?.apply { makeAccessible() }
            }.getOrNull()
        }

        /**
         * 用 [src] 的前景/背景/单色图层重建一个无描边版本
         */
        fun from(src: AdaptiveIconDrawable): AdaptiveIconDrawable =
            runCatching {
                NoStrokeAdaptiveIconDrawable(src.background, src.foreground, src.monochrome)
            }.getOrDefault(src)
    }
}
