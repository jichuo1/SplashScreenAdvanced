package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import dev.lackluster.hyperx.ui.component.IconSize
import dev.lackluster.hyperx.ui.component.ImageIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用列表行的图标: 按需加载 + 有界缓存
 *
 * 三个应用列表页 (CustomScope / BgIndividual / MinDuration 等) 原先共用同一套写法, 有两个问题:
 *
 * 1. 进入页面时对**全部**已安装应用 `loadIcon()`, 几百个 Drawable 随列表数据常驻内存
 * 2. `item.icon.toBitmap().asImageBitmap()` 直接写在 item 内且没有 remember,
 *    滚动、搜索输入、勾选都会触发重组, 每次都重新光栅化一张 Bitmap
 *
 * 这里改为: 只有行真正被组合时才在 IO 线程加载, 按显示尺寸光栅化 (而不是图标的 intrinsic 尺寸),
 * 并用一个 [ICON_CACHE_SIZE] 条目的 LRU 缓存兜住来回滚动。加载中返回 null ——
 * [SwitchPreference] 等组件的 icon 参数本身可空。
 */
@Composable
fun rememberAppIcon(packageName: String, size: IconSize = IconSize.App): ImageIcon? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.dp.toPx() }.toInt().coerceAtLeast(1)

    val bitmap by produceState<ImageBitmap?>(cachedIcon(packageName), packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { loadAppIcon(context, packageName, sizePx) }
    }

    return bitmap?.let { ImageIcon(bitmap = it, size = size) }
}

/** LRU 上限。按 40dp @ xxhdpi (120px) 估算, 单张约 57KB, 整体控制在几 MB 量级 */
private const val ICON_CACHE_SIZE = 64

private val iconCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
        size > ICON_CACHE_SIZE
}

private fun cachedIcon(packageName: String): ImageBitmap? =
    synchronized(iconCache) { iconCache[packageName] }

private fun loadAppIcon(context: Context, packageName: String, sizePx: Int): ImageBitmap? =
    runCatching {
        context.packageManager.getApplicationIcon(packageName)
            .toBitmap(sizePx, sizePx)
            .asImageBitmap()
    }.getOrNull()?.also {
        synchronized(iconCache) { iconCache[packageName] = it }
    }
