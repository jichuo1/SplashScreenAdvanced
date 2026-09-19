package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.SplashScreenAdvanced.xposedmodule.utils.sr.IconCacheProvider

/**
 * 离线超分缓存的读取客户端（运行在 SystemUI 进程）
 *
 * 走 `ContentResolver.openFileDescriptor` 拿到 FD 后直接解码, 避免把整张图先读进 byte[]。
 * Provider 在"无缓存 / 尺寸不匹配 / 文件缺失"时都会抛 `FileNotFoundException`, 这里统一当作
 * **未命中**处理并返回 `null`, 由调用方回退到实时路径——离线缓存永远是"锦上添花", 任何异常
 * 都不应影响启动遮罩本身的可用性。
 */
internal object IconCacheClient {

    private const val CACHE_MAX_ENTRIES = 16

    /** 解码结果比原始数据贵, 命中后常驻复用 */
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > CACHE_MAX_ENTRIES
    }

    fun fetch(context: Context, packageName: String, targetSize: Int): Drawable? {
        if (packageName.isEmpty() || targetSize <= 0) return null

        val key = "$packageName|$targetSize"
        synchronized(cache) { cache[key] }?.let { return BitmapDrawable(it) }

        val bitmap = runCatching {
            val uri = IconCacheProvider.uriFor(packageName, targetSize)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
            }
        }.getOrNull() ?: return null

        synchronized(cache) { cache[key] = bitmap }
        return BitmapDrawable(bitmap)
    }

    fun clearCache() = synchronized(cache) { cache.clear() }
}
