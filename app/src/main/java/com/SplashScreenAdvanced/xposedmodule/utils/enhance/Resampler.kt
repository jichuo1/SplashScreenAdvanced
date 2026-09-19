package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * 启动遮罩图标的重采样内核库
 *
 * 设计取向是"够用、可预测、零依赖":
 * - **上采样**用 4-tap Mitchell-Netravali: 抗锯齿好且振铃远低于 Lanczos, 对图标这类高对比
 *   边缘对象更安全(振铃会在图标边缘形成"描边光晕");
 * - **降采样**用整数因子 Box 平均: SSAA 的标准收敛方式, 恒定 O(1)/像素;
 * - **锐化**用边缘感知 Unsharp Mask: 平坦区不放大噪点, 强边缘不产生光晕。
 *
 * 全部为纯 Kotlin + [IntArray], 不使用已废弃的 RenderScript, 也不引入 JNI, 便于 ART 上的
 * JIT 优化。所有会引入透明边缘渗色的步骤都要求调用方**先在预乘域操作**。
 *
 * 约定: 数组一律是 ARGB_8888 打包成 Int 的行优先(row-major)一维数组。
 */
internal object Resampler {

    /** LUT 相位精度。32 档对肉眼已无差别, 再高只是徒增构建成本 */
    private const val PHASES = 32

    /** Mitchell 的支撑半径 */
    private const val SUPPORT = 2

    /** 每趟卷积的采样点数（4-tap） */
    private const val TAP_COUNT = SUPPORT * 2

    /**
     * 相位权重表（4-tap）
     *
     * 与缩放比 **无关**: 放大时核函数不拉伸, 权重只随小数相位变化, 所以整张表可以常驻复用。
     * 每行已归一化, 避免浮点误差带来的整体亮度漂移。
     */
    private val MITCHELL_LUT: Array<FloatArray> by lazy {
        Array(PHASES) { phase ->
            val frac = phase / PHASES.toFloat()
            val w = FloatArray(TAP_COUNT) { k -> mitchell((k - 1) - frac) }
            var sum = 0f
            for (v in w) sum += v
            if (sum != 0f) for (i in w.indices) w[i] /= sum
            w
        }
    }

    // ---------------------------------------------------------------- 核函数

    /**
     * Mitchell-Netravali 三次核（B = C = 1/3, 支撑 [-2, 2]）
     */
    private fun mitchell(x: Float): Float {
        val ax = abs(x)
        val b = 1f / 3f
        val c = 1f / 3f
        return when {
            ax < 1f -> (
                    (12f - 9f * b - 6f * c) * ax * ax * ax +
                            (-18f + 12f * b + 6f * c) * ax * ax +
                            (6f - 2f * b)
                    ) / 6f

            ax < 2f -> (
                    (-b - 6f * c) * ax * ax * ax +
                            (6f * b + 30f * c) * ax * ax +
                            (-12f * b - 48f * c) * ax +
                            (8f * b + 24f * c)
                    ) / 6f

            else -> 0f
        }
    }

    // ---------------------------------------------------------------- alpha 预乘

    /**
     * alpha 预乘
     *
     * 插值/滤波必须在预乘域进行, 否则完全透明像素的颜色分量会被"拖"进可见区域, 在图标
     * 边缘形成深色描边(黑边)。这是 SSAA 落地时最容易踩的坑。
     */
    fun premultiply(src: IntArray): IntArray {
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val a = p ushr 24
            out[i] = when (a) {
                255 -> p
                0 -> 0
                else -> {
                    val r = ((p shr 16) and 0xFF) * a / 255
                    val g = ((p shr 8) and 0xFF) * a / 255
                    val b = (p and 0xFF) * a / 255
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }

    /** alpha 反预乘（与 [premultiply] 配对） */
    fun unpremultiply(src: IntArray): IntArray {
        val out = IntArray(src.size)
        for (i in src.indices) {
            val p = src[i]
            val a = p ushr 24
            out[i] = if (a == 255 || a == 0) p else {
                val r = (((p shr 16) and 0xFF) * 255 / a).coerceAtMost(255)
                val g = (((p shr 8) and 0xFF) * 255 / a).coerceAtMost(255)
                val b = ((p and 0xFF) * 255 / a).coerceAtMost(255)
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 上采样

    /**
     * 4-tap Mitchell 上采样（要求 [dstW] >= [srcW] 且 [dstH] >= [srcH]）
     *
     * 分离卷积: 先水平（srcW → dstW）再垂直（srcH → dstH）, 复杂度为
     * `dstW*srcH*4 + dstW*dstH*4`, 相比二维卷积的 16 tap 省一半以上。
     *
     * @param src 已**预乘**的源像素
     */
    fun upscaleMitchell(src: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        val lut = MITCHELL_LUT
        val lastSrcX = srcW - 1
        val lastSrcY = srcH - 1

        // 水平趟: (srcH, srcW) -> (srcH, dstW)
        val tmp = IntArray(dstW * srcH)
        val scaleX = srcW.toFloat() / dstW
        for (y in 0 until srcH) {
            val rowOff = y * srcW
            val outOff = y * dstW
            for (x in 0 until dstW) {
                val center = (x + 0.5f) * scaleX - 0.5f
                val base = floor(center).toInt()
                val frac = center - base
                val w = lut[(frac * PHASES).toInt().coerceIn(0, PHASES - 1)]
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for (k in 0 until TAP_COUNT) {
                    val sx = (base - 1 + k).coerceIn(0, lastSrcX)
                    val p = src[rowOff + sx]
                    val wt = w[k]
                    a += (p ushr 24) * wt
                    r += ((p shr 16) and 0xFF) * wt
                    g += ((p shr 8) and 0xFF) * wt
                    b += (p and 0xFF) * wt
                }
                tmp[outOff + x] = pack(a, r, g, b)
            }
        }

        // 垂直趟: (srcH, dstW) -> (dstH, dstW)
        val out = IntArray(dstW * dstH)
        val scaleY = srcH.toFloat() / dstH
        for (y in 0 until dstH) {
            val center = (y + 0.5f) * scaleY - 0.5f
            val base = floor(center).toInt()
            val frac = center - base
            val w = lut[(frac * PHASES).toInt().coerceIn(0, PHASES - 1)]
            val outOff = y * dstW
            for (x in 0 until dstW) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for (k in 0 until TAP_COUNT) {
                    val sy = (base - 1 + k).coerceIn(0, lastSrcY)
                    val p = tmp[sy * dstW + x]
                    val wt = w[k]
                    a += (p ushr 24) * wt
                    r += ((p shr 16) and 0xFF) * wt
                    g += ((p shr 8) and 0xFF) * wt
                    b += (p and 0xFF) * wt
                }
                out[outOff + x] = pack(a, r, g, b)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 降采样（SSAA 收敛）

    /**
     * 整数因子 Box 降采样（SSAA 的标准收敛方式）
     *
     * @param factor 超采样倍数（2 或 3）
     * @param dstSize 输出边长, 源边长必须是 `dstSize * factor`
     * @param src 已**预乘**的源像素
     */
    fun boxDownsample(src: IntArray, factor: Int, dstSize: Int): IntArray {
        val srcW = dstSize * factor
        val n = factor * factor
        val out = IntArray(dstSize * dstSize)
        for (y in 0 until dstSize) {
            val rowBase = y * factor * srcW
            val outOff = y * dstSize
            for (x in 0 until dstSize) {
                val colBase = rowBase + x * factor
                var a = 0; var r = 0; var g = 0; var b = 0
                for (dy in 0 until factor) {
                    val off = colBase + dy * srcW
                    for (dx in 0 until factor) {
                        val p = src[off + dx]
                        a += p ushr 24
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                out[outOff + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 锐化

    /**
     * 边缘感知 Unsharp Mask
     *
     * `dst = src + amount × mask × (src - blur)`, 其中 `mask = min(1, 边缘强度 / tau)`。
     * 掩码的作用是**只在有细节处锐化**: 平坦区不会被放大噪点, 强边缘不会产生过冲光晕。
     *
     * @param amount 强度, 建议 0.15~0.35; <= 0 时原样返回
     * @param tau    边缘掩码阈值（0~255 域）, 建议 8
     * @param src    已**预乘**的像素
     */
    fun unsharpMask(src: IntArray, w: Int, h: Int, amount: Float, tau: Int): IntArray {
        if (amount <= 0f || tau <= 0) return src
        val blur = boxBlurSeparable(src, w, h)
        val out = IntArray(w * h)
        for (i in src.indices) {
            val p = src[i]
            val a = p ushr 24
            if (a == 0) {
                out[i] = 0
                continue
            }
            val q = blur[i]
            val hr = ((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)
            val hg = ((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)
            val hb = (p and 0xFF) - (q and 0xFF)
            // 用三通道绝对差均值估计边缘强度: 比单通道更稳, 也比求平方和便宜
            val mag = (abs(hr) + abs(hg) + abs(hb)) / 3
            val k = amount * min(1f, mag.toFloat() / tau)

            val r = ((p shr 16) and 0xFF) + k * hr
            val g = ((p shr 8) and 0xFF) + k * hg
            val b = (p and 0xFF) + k * hb
            out[i] = (a shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
        }
        return out
    }

    /**
     * 分离式 3×3 均值模糊（[1,1,1] 水平 × [1,1,1] 垂直 = 3×3 box）
     *
     * 相比逐像素 9 次读取的二维实现, 分离后每像素只需 6 次读取。
     * 边界按**边缘复制**处理（越界位置复用边界像素）, 全程整数运算。
     */
    private fun boxBlurSeparable(src: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(w * h)
        // 水平趟
        for (y in 0 until h) {
            val row = y * w
            val first = src[row]
            val last = src[row + w - 1]
            for (x in 0 until w) {
                val p0 = if (x > 0) src[row + x - 1] else first
                val p1 = src[row + x]
                val p2 = if (x < w - 1) src[row + x + 1] else last
                tmp[row + x] = avg3(p0, p1, p2)
            }
        }
        // 垂直趟
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val rowUp = (if (y > 0) y - 1 else 0) * w
            val row = y * w
            val rowDown = (if (y < h - 1) y + 1 else h - 1) * w
            for (x in 0 until w) {
                out[row + x] = avg3(tmp[rowUp + x], tmp[row + x], tmp[rowDown + x])
            }
        }
        return out
    }

    /** 三像素逐通道平均（+1 实现四舍五入） */
    private fun avg3(p0: Int, p1: Int, p2: Int): Int {
        val a = ((p0 ushr 24) + (p1 ushr 24) + (p2 ushr 24) + 1) / 3
        val r = (((p0 shr 16) and 0xFF) + ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) + 1) / 3
        val g = (((p0 shr 8) and 0xFF) + ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) + 1) / 3
        val b = ((p0 and 0xFF) + (p1 and 0xFF) + (p2 and 0xFF) + 1) / 3
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ---------------------------------------------------------------- 工具

    private fun clamp255(v: Float): Int = when {
        v <= 0f -> 0
        v >= 255f -> 255
        else -> (v + 0.5f).toInt()
    }

    private fun pack(a: Float, r: Float, g: Float, b: Float): Int =
        (clamp255(a) shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)

    /**
     * 以给定边长把 [Drawable] 栅格化为像素数组
     *
     * 与模块既有的 `drawable2Bitmap` 一样会**还原 bounds**——传入的 Drawable 很可能来自宿主的
     * 图标缓存, 改动其 bounds 会污染宿主后续的绘制。
     */
    fun rasterize(drawable: Drawable, size: Int): IntArray? = try {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val originalBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        drawable.bounds = originalBounds
        canvas.setBitmap(null)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()
        pixels
    } catch (_: Throwable) {
        null
    }

    /** 像素数组还原为 [Bitmap]（ARGB_8888, 独占新建） */
    fun toBitmap(pixels: IntArray, size: Int): Bitmap =
        createBitmap(size, size).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
}
