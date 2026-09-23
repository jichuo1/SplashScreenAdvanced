@file:JvmName("GraphicUtils")

package com.SplashScreenAdvanced.xposedmodule.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette

/**
 * Drawable 图标转 Bitmap
 *
 * 返回值始终是**新建的、[size] x [size] 的独立 Bitmap**。
 *
 * 这里刻意不再为 [BitmapDrawable] 走"直接返回 `drawable.bitmap`"的捷径:
 * 那个 Bitmap 是宿主 Drawable (很可能来自 PackageManager 的图标缓存) 内部持有的实例,
 * 而调用方 (如替换 `BaseIconFactory.createIconBitmap` 的返回值) 会按"独占新建"的语义
 * 去使用甚至 recycle 它; 而且它的尺寸也未必等于请求的 [size]。
 *
 * 也不走 androidx `Drawable.toBitmap(width, height)`: 它不保证还原 bounds, 且可能复用已有 Bitmap。
 *
 * @receiver 待转换的 Drawable 图标
 * @param size 生成此大小的 Bitmap
 * @return [Bitmap]
 */
fun Drawable.drawable2Bitmap(size: Int): Bitmap {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    // drawable 是共享实例, setBounds 会影响宿主后续的绘制, 画完必须还原
    val originalBounds = Rect(bounds)
    setBounds(0, 0, size, size)
    draw(canvas)
    bounds = originalBounds
    canvas.setBitmap(null)
    return bitmap
}

/** Palette 量化边长。再大只会增加启动链路耗时, 对主色几乎没有收益 */
private const val PALETTE_MAX_SIZE = 48

/** 取色失败时的回退色 */
private val FALLBACK_LIGHT_COLOR = "#F5F5F5".toColorInt()
private val FALLBACK_DARK_COLOR = "#1C2833".toColorInt()

private val hsvScratch = ThreadLocal.withInitial { FloatArray(3) }

/**
 * 根据 Bitmap 获取背景颜色
 *
 * @receiver 从中获取颜色的图片
 * @param isLight 是否为浅色模式
 * @return [Int]
 */
fun Bitmap.getBgColor(isLight: Boolean): Int {
    val hsv = hsvScratch.get()!!
    val sample: Bitmap
    val recycleSample: Boolean
    if (width > PALETTE_MAX_SIZE || height > PALETTE_MAX_SIZE) {
        sample = Bitmap.createScaledBitmap(this, PALETTE_MAX_SIZE, PALETTE_MAX_SIZE, true)
        recycleSample = sample !== this
    } else {
        sample = this
        recycleSample = false
    }
    try {
        val color = Palette.from(sample)
            .maximumColorCount(8).generate()
            .getDominantColor(if (isLight) FALLBACK_LIGHT_COLOR else FALLBACK_DARK_COLOR)
        Color.colorToHSV(color, hsv)
        if (isLight) {
            hsv[1] = hsv[1] - 0.4f
            hsv[2] = hsv[2] + 0.2f
        } else {
            hsv[1] = hsv[1] - 0.2f
            hsv[2] = hsv[2] - 0.7f
        }
        hsv[1] = hsv[1].coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    } finally {
        if (recycleSample) sample.recycle()
    }
}

/**
 * 从图标采样主色。位图在取色后立即回收, 避免启动遮罩路径上堆积 112px 缓冲。
 */
fun Drawable.drawableDominantColor(isLight: Boolean, sampleSize: Int = PALETTE_MAX_SIZE): Int {
    val bitmap = drawable2Bitmap(sampleSize)
    return try {
        bitmap.getBgColor(isLight)
    } finally {
        bitmap.recycle()
    }
}

/**
 * 将给定的 Drawable 包装为居中方形 Drawable，**不做栅格化**
 *
 * 旧实现会先栅格化源、再画进一张 `max(w,h)` 的方形位图 —— 每次调用付一次位图分配
 * + 软件 Canvas 绘制。包装实现零分配零拷贝: 源由宿主最终的栅格化统一绘制。
 *
 * @receiver 需要转换的 Drawable（一般是 MIUI 大图标的 1x2/2x1 规格）
 * @return 方形 Drawable；源本身已是方形或尺寸非法时原样返回
 */
fun Drawable.convertToSquareDrawable(): Drawable {
    val src = this
    val w = src.intrinsicWidth
    val h = src.intrinsicHeight
    if (w <= 0 || h <= 0 || w == h) return src
    val side = maxOf(w, h)

    return object : Drawable() {
        override fun draw(canvas: Canvas) = src.draw(canvas)

        override fun onBoundsChange(b: Rect) {
            super.onBoundsChange(b)
            // 与旧位图实现一致: 方形区域边长取 max(w,h), 源按原始比例居中
            val cw = w * b.width() / side
            val ch = h * b.height() / side
            val l = b.left + (b.width() - cw) / 2
            val t = b.top + (b.height() - ch) / 2
            src.setBounds(l, t, l + cw, t + ch)
        }

        override fun getIntrinsicWidth() = side
        override fun getIntrinsicHeight() = side
        override fun setAlpha(alpha: Int) {
            src.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            src.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
