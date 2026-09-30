package com.SplashScreenAdvanced.xposedmodule.wrapper

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * 仅绘制自适应图标前景层的 Drawable (不绘制背景, 不套遮罩)
 *
 * 图层几何与 AdaptiveIconDrawable 一致: 图层边界为视图边界以中心向外扩到 1.5 倍
 *
 * 字段名 mForegroundDrawable 被 IconEnhanceEngine.unwrapForeground 反射读取, 不可改名
 */
class AdaptiveForegroundLayerDrawable(
    private val mForegroundDrawable: Drawable
) : Drawable(), Drawable.Callback {

    init {
        mForegroundDrawable.callback = this
    }

    override fun onBoundsChange(bounds: Rect) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val halfW = bounds.width() * LAYER_SCALE / 2f
        val halfH = bounds.height() * LAYER_SCALE / 2f
        mForegroundDrawable.setBounds(
            (cx - halfW).toInt(), (cy - halfH).toInt(),
            (cx + halfW).toInt(), (cy + halfH).toInt()
        )
    }

    override fun draw(canvas: Canvas) {
        mForegroundDrawable.draw(canvas)
    }

    override fun getIntrinsicWidth(): Int = scaledIntrinsic(mForegroundDrawable.intrinsicWidth)

    override fun getIntrinsicHeight(): Int = scaledIntrinsic(mForegroundDrawable.intrinsicHeight)

    private fun scaledIntrinsic(size: Int): Int = if (size <= 0) -1 else (size / LAYER_SCALE).toInt()

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        mForegroundDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mForegroundDrawable.colorFilter = colorFilter
    }

    override fun mutate(): Drawable {
        mForegroundDrawable.mutate()
        return this
    }

    override fun getConstantState(): ConstantState? {
        val fgState = mForegroundDrawable.constantState ?: return null
        return object : ConstantState() {
            override fun newDrawable(): Drawable = AdaptiveForegroundLayerDrawable(fgState.newDrawable())
            override fun getChangingConfigurations(): Int = fgState.changingConfigurations
        }
    }

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    private companion object {
        const val LAYER_SCALE = 1.5f
    }
}
