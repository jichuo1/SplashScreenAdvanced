package com.gswxxn.restoresplashscreen.wrapper

import android.graphics.drawable.Drawable
import com.gswxxn.restoresplashscreen.hook.utils.ReflectCache

/**
 * SplashScreenView.Builder 的包装类
 */
class SplashScreenViewBuilderWrapper private constructor(private val builder: Any) {

    companion object {
        /**
         * 为给定的 SplashScreenView.Builder 创建包装实例
         *
         * 这里**不做实例缓存**: 宿主每次应用启动都会 new 一个 Builder, 按 Builder 缓存等于把
         * 每一个 Builder (及其持有的 Context / 图标 Drawable / branding Drawable) 永久钉在
         * 静态 Map 里, 在常驻的 SystemUI 进程中是无界泄漏; 且 build() 不保证单线程,
         * 普通 HashMap 并发写还会丢条目。
         *
         * 包装类自身无状态, 唯一的开销 (反射解析 setBackgroundColor / setBrandingDrawable)
         * 已经由 [ReflectCache] 按类缓存, 所以每次新建一个轻量对象即可。
         *
         * @throws IllegalArgumentException 传入的 builder 不是 SplashScreenView.Builder 实例时
         */
        fun getInstance(builder: Any): SplashScreenViewBuilderWrapper {
            if (builder.javaClass.name != $$"android.window.SplashScreenView$Builder") {
                throw IllegalArgumentException("Builder must be of type SplashScreenViewBuilder")
            }

            return SplashScreenViewBuilderWrapper(builder)
        }
    }

    /** 设置整体背景颜色 */
    fun setBackgroundColor(backgroundColor: Int) {
        ReflectCache.invokeMethod<Any>(builder, "setBackgroundColor", backgroundColor)
    }

    /** 设置 branding view 的 Drawable 与尺寸 */
    fun setBrandingDrawable(branding: Drawable?, width: Int, height: Int) {
        ReflectCache.invokeMethod<Any>(builder, "setBrandingDrawable", branding, width, height)
    }
}
