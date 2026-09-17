@file:JvmName("GraphicUtils")

package com.SplashScreenAdvanced.xposedmodule.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
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
 * 缩小图标, 以避免后续使用 setRenderEffect 模糊图标时出现毛边问题
 *
 * @receiver 需要创建阴影的 Drawable
 * @param context 上下文，用于获取资源
 * @param oriIconSize 原始图标大小
 * @param blurIconSize 待模糊图标大小
 * @param cornerRadius 模糊图标圆角大小
 * @return 待模糊图标 Drawable
 */
fun Drawable.createShadowedIcon(
    context: Context,
    oriIconSize: Int,
    blurIconSize: Int,
    cornerRadius: Float
): Drawable {
    val originalSize = intrinsicWidth
    val ratio = (oriIconSize.toDouble() / originalSize).coerceAtMost(1.0).toFloat()
    val scaledSize = (originalSize * ratio).toInt()
    val shadowSize = blurIconSize / 4
    val shadowBitmap = createBitmap(scaledSize + shadowSize, scaledSize + shadowSize)
    val canvas = Canvas(shadowBitmap)
    val offset = (shadowSize / 2).toFloat()

    val checkpoint = canvas.saveLayerAlpha(0f, 0f, shadowBitmap.width.toFloat(), shadowBitmap.height.toFloat(), 90)
    canvas.translate(offset, offset)
    canvas.clipPath(
        Path().apply {
            addRoundRect(
                RectF(0f, 0f, scaledSize.toFloat(), scaledSize.toFloat()),
                cornerRadius, cornerRadius, Path.Direction.CW
            )
        }
    )
    val originalBounds = Rect(bounds)
    setBounds(0, 0, scaledSize, scaledSize)
    draw(canvas)
    bounds = originalBounds
    canvas.restoreToCount(checkpoint)

    return shadowBitmap.toDrawable(context.resources)
}

/**
 * 将给定的 Drawable 转换为一个新的正方形 Drawable，其空白区域用透明色填充。
 *
 * @receiver 需要转换的 Drawable
 * @param resources 应用程序的资源，用于将 Bitmap 转换回 Drawable。
 * @return 返回一个新的正方形 Drawable，其空白区域用透明色填充。
 */
fun Drawable.convertToSquareDrawable(resources: Resources): Drawable {
    val originalBitmap = if (this is BitmapDrawable) {
        bitmap
    } else {
        createBitmap(intrinsicWidth, intrinsicHeight).also { bitmap ->
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }

    val size = maxOf(originalBitmap.width, originalBitmap.height)
    val squareBitmap = createBitmap(size, size)
    val canvas = Canvas(squareBitmap)
    val x = (size - originalBitmap.width) / 2
    val y = (size - originalBitmap.height) / 2
    canvas.drawBitmap(originalBitmap, x.toFloat(), y.toFloat(), null)
    return squareBitmap.toDrawable(resources)
}
