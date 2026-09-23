package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import com.SplashScreenAdvanced.xposedmodule.utils.sr.IconCacheProvider
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * 未命中负缓存: key -> 上次未命中时刻 (elapsedRealtime)
     *
     * 没有离线缓存的应用是常态, 不记 miss 的话每次启动都要付一次 Provider binder 调用 +
     * FileNotFoundException。TTL 到期自动放行重试, 使"之后跑过超分工厂"的缓存能生效
     */
    private val misses = ConcurrentHashMap<String, Long>()
    private const val MISS_TTL_MS = 60_000L

    /**
     * Provider 进程预热
     *
     * `openFileDescriptor` 在模块进程未运行时会同步拉起它(冷启动 + Provider attach, 可达
     * ~0.5-1s), 而这发生在首启启动遮罩的图标栅格化线程上。这里用 `getType` 做一次纯 binder
     * 探活 —— 它不读索引不碰文件, 是能让平台把 Provider 进程拉起来的最轻调用。
     * 应在注入完成后的后台线程调用一次。
     */
    fun prewarm(context: Context) {
        runCatching { context.contentResolver.getType(IconCacheProvider.uriFor("", 0)) }
    }

    /**
     * @param sourceDir 应用安装目录(sourceDir 的随机段在每次应用更新时都会变), 并入缓存键后
     *                  应用更新 + 重新扫描产出的新文件不会再被旧位图命中, 无需显式失效
     */
    fun fetch(context: Context, packageName: String, targetSize: Int, sourceDir: String?): Drawable? {
        if (packageName.isEmpty() || targetSize <= 0) return null

        val key = "$packageName|$targetSize|$sourceDir"
        synchronized(cache) { cache[key] }?.let { return BitmapDrawable(it) }

        misses[key]?.let { missedAt ->
            if (SystemClock.elapsedRealtime() - missedAt < MISS_TTL_MS) return null
            misses.remove(key)
        }

        val bitmap = runCatching {
            val uri = IconCacheProvider.uriFor(packageName, targetSize)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
            }
        }.getOrNull()

        if (bitmap == null) {
            misses[key] = SystemClock.elapsedRealtime()
            return null
        }
        synchronized(cache) { cache[key] = bitmap }
        return BitmapDrawable(bitmap)
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
        misses.clear()
    }
}
