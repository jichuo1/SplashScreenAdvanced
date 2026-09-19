package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.SystemClock
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.utils.RemotePreferences.get
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import kotlin.math.min

/**
 * 启动遮罩图标画质增强引擎（实时路径）
 *
 * ## 它解决什么
 *
 * 宿主链路在栅格化时会把图标缩到 `starting_surface_default_icon_size`(108dp) 再放大到
 * `starting_surface_icon_size`(160dp) 绘制。其中"放大"这一半由 [地基修复]（强制按目标尺寸
 * 栅格化）消除; 本引擎负责另一半 —— **位图源在被栅格化时用的是系统默认的双线性插值**,
 * 对低分辨率图标仍会留下模糊与锯齿。
 *
 * ## 为什么不做 SSAA
 *
 * 矢量的最优处理是"按目标尺寸重渲染", 而这恰好就是地基修复本身做的事 —— 再叠加 2x/3x
 * 超采样只是用数倍的渲染成本换取极小的边缘增益, 性价比不成立。所以:
 *
 * | 源类型 | 处理 |
 * |:---|:---|
 * | 矢量 / 自适应(矢量前景) | **不处理**, 交给地基修复做到无损 |
 * | 位图 | 以**源原生尺寸**栅格化 → Mitchell 重采样 → 边缘感知锐化 |
 *
 * 以源原生尺寸为起点很关键: 先放大再缩小会引入两次重采样, 纯粹是画质浪费。
 *
 * ## 稳定性
 *
 * - 全流程包在 `runCatching` 内, 任何失败都返回 `null`, 调用方退化为"仅地基修复";
 * - 分步检查时间预算, 超时立即停止后续步骤(用画质换确定性);
 * - 结果按 `包名|组件|目标尺寸|档位` 缓存, 同一应用二次启动零成本。
 */
internal object IconEnhanceEngine {

    /** 缓存条目上限。图标是常驻数据, 但 SystemUI 内存敏感, 不宜过大 */
    private const val CACHE_MAX_ENTRIES = 24

    /** 单次增强的时间预算(毫秒), 按档位放宽 */
    private fun budgetMs(level: Int): Long = when (level) {
        1 -> 12L
        2 -> 24L
        else -> 40L
    }

    /** 锐化强度, 按档位递增 */
    private fun sharpenAmount(level: Int): Float = when (level) {
        1 -> 0.12f
        2 -> 0.22f
        else -> 0.32f
    }

    /** 边缘掩码阈值(0~255 域) */
    private const val SHARPEN_TAU = 8

    /** 访问序 LRU */
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > CACHE_MAX_ENTRIES
    }

    /** 当前档位, 0 表示关闭 */
    private val level: Int get() = Preferences.Icon.ENHANCE_LEVEL.get()

    /**
     * 尝试增强图标
     *
     * @param src        待处理的图标 Drawable（宿主传入的原始对象, 不会被修改）
     * @param targetSize 最终绘制尺寸（宿主算好的 `iconSize`）
     * @param cacheKey   调用方提供的稳定标识（建议 `包名|组件`）
     * @return 增强后的 Drawable; **`null` 表示"本次不处理"**, 调用方应保留原 Drawable 并
     *         只做地基修复
     */
    fun enhance(src: Drawable, targetSize: Int, cacheKey: String): Drawable? {
        if (targetSize <= 0) return null
        val lv = level
        if (lv <= 0) return null

        // 矢量源交给地基修复即可做到无损, 重采样没有增益
        if (isVectorLike(src)) return null

        val key = "$cacheKey|$targetSize|$lv"
        synchronized(cache) { cache[key] }?.let { return BitmapDrawable(it) }

        val bitmap = runCatching { render(src, targetSize, lv) }
            .onFailure { XMLog.e(t = it) { "IconEnhanceEngine: render failed" } }
            .getOrNull()
            ?: return null

        synchronized(cache) { cache[key] = bitmap }
        return BitmapDrawable(bitmap)
    }

    /** 档位变化或应用更新后清空缓存 */
    fun clearCache() = synchronized(cache) { cache.clear() }

    /**
     * 离线增强（供扫描服务使用）
     *
     * 与实时路径共用同一套内核, 区别有两点:
     * - **不做矢量判定**: 离线场景下矢量源也需要产出位图, 直接按目标尺寸栅格化即可(无损);
     * - **不受预算与 LRU 约束**: 离线可以慢慢算, 用最高档强度换取更好的结果。
     *
     * @return 增强后的位图; `null` 表示失败, 调用方直接跳过该应用即可
     */
    fun enhanceOffline(drawable: Drawable, targetSize: Int): Bitmap? = runCatching {
        if (targetSize <= 0) return@runCatching null
        render(drawable, targetSize, OFFLINE_LEVEL)
    }.getOrNull()

    /** 离线固定使用最高档强度 */
    private const val OFFLINE_LEVEL = 3

    // ---------------------------------------------------------------- 内部实现

    private fun render(src: Drawable, targetSize: Int, lv: Int): Bitmap? {
        val deadline = SystemClock.uptimeMillis() + budgetMs(lv)

        // 以源的原生分辨率为起点: 超出目标的部分没有意义, 还会把噪声一并放大
        val raw = unwrapForeground(src)
        val intrinsic = maxOf(raw.intrinsicWidth, raw.intrinsicHeight)
        val rasterSize = (if (intrinsic <= 0) targetSize else min(intrinsic, targetSize))
            .coerceAtLeast(1)

        var pixels = Resampler.rasterize(src, rasterSize) ?: return null
        pixels = Resampler.premultiply(pixels)

        // 只有确实需要放大时才走 Mitchell; 源已达目标尺寸时保持 1:1, 不做无谓重采样
        if (rasterSize < targetSize && SystemClock.uptimeMillis() < deadline) {
            pixels = Resampler.upscaleMitchell(pixels, rasterSize, rasterSize, targetSize, targetSize)
        }

        if (SystemClock.uptimeMillis() < deadline) {
            pixels = Resampler.unsharpMask(pixels, targetSize, targetSize, sharpenAmount(lv), SHARPEN_TAU)
        }

        pixels = Resampler.unpremultiply(pixels)
        return Resampler.toBitmap(pixels, targetSize)
    }

    /**
     * 是否为"矢量类"图标（有矢量源就不该做重采样）
     *
     * 宿主会先用 `AdaptiveForegroundDrawable` 把非自适应图标包一层, 所以这里要先拆一层包装
     * 才能看到真正的源。字段名在 AOSP 12~15 稳定, 反射失败时按位图处理（安全兜底: 位图路径
     * 对源本就足够清晰的图标几乎无副作用）。
     */
    private fun isVectorLike(drawable: Drawable): Boolean = when (val d = unwrapForeground(drawable)) {
        is AdaptiveIconDrawable -> unwrapForeground(d.foreground ?: return false) is VectorDrawable
        is VectorDrawable -> true
        else -> false
    }

    private fun unwrapForeground(drawable: Drawable): Drawable =
        if (drawable is AdaptiveIconDrawable || drawable is VectorDrawable) drawable
        else runCatching {
            drawable.javaClass.getDeclaredField("mForegroundDrawable")
                .apply { isAccessible = true }
                .get(drawable) as? Drawable
        }.getOrNull() ?: drawable
}
