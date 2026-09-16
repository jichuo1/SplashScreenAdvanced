package com.gswxxn.restoresplashscreen.wrapper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import com.gswxxn.restoresplashscreen.hook.utils.ReflectCache

/**
 * 用于 HyperOS 的透明背景 AdaptiveIconDrawable
 */
class TransparentAdaptiveIconDrawable(
    foregroundDrawable: Drawable
) : AdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), foregroundDrawable) {
    private var mLayersShader: Shader?
        get() = ReflectCache.getField(this, "mLayersShader")
        set(value) {
            ReflectCache.setField(this, "mLayersShader", value)
        }
    private val mCanvas: Canvas?
        get() = ReflectCache.getField(this, "mCanvas")
    private val mLayersBitmap: Bitmap?
        get() = ReflectCache.getField(this, "mLayersBitmap")
    private val mPaint: Paint?
        get() = ReflectCache.getField(this, "mPaint")
    private val mMaskScaleOnly: Path?
        get() = ReflectCache.getField(this, "mMaskScaleOnly")

    /**
     * 继承修改自 AdaptiveIconDrawable
     * 详见 [AdaptiveIconDrawable.draw]
     *
     * 每个反射字段在一次绘制里只读一次并存进局部变量: 这些 getter 每次都要走 [ReflectCache]
     * 查询 (含 key 对象分配), 而 draw() 在渲染路径上会被反复调用。
     *
     * 另外全部改为可空处理: 这些都是 AdaptiveIconDrawable 的私有字段, 一旦某个 Android 版本改了
     * 字段名, 原先的 `!!` 会在 draw() 里抛 NPE 进渲染流程; 取不到时直接放弃绘制更安全。
     */
    override fun draw(canvas: Canvas) {
        val layersBitmap = mLayersBitmap ?: return
        val paint = mPaint ?: return

        if (mLayersShader == null) {
            val layersCanvas = mCanvas ?: return

            // 修改为透明色清空画布
            layersCanvas.setBitmap(layersBitmap)
            layersCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // 绘制背景图层
            background?.draw(layersCanvas)

            // 绘制前景图层
            foreground?.setBounds(0, 0, bounds.width(), bounds.height())
            foreground?.draw(layersCanvas)

            // 创建位图着色器
            val shader = BitmapShader(layersBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            mLayersShader = shader
            paint.shader = shader
        }

        val mask = mMaskScaleOnly ?: return
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawPath(mask, paint)
        canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
    }
}
