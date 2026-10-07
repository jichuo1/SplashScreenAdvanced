package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.graphics.drawable.Drawable

/** 无法独占绘制输入时跳过增强，保持宿主图标；mutate 隔离图层和颜色状态。 */
internal fun Drawable.copyForRendering(): Drawable? = runCatching {
    val copy = constantState?.newDrawable()?.mutate() ?: return null
    if (copy === this) return null
    copy.bounds = bounds
    copy.state = state.copyOf()
    copy.level = level
    copy.layoutDirection = layoutDirection
    copy.alpha = alpha
    copy.colorFilter = colorFilter
    copy
}.getOrNull()
