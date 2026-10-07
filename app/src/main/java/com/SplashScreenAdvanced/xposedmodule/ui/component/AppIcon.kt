package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeSettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val iconCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/** Only visible ListView rows load icons; stale recycled rows never receive a previous result. */
fun NativeSettingsActivity.bindAppIcon(view: ImageView, packageName: String) {
    (view.getTag(com.SplashScreenAdvanced.xposedmodule.R.id.native_icon_job) as? Job)?.cancel()
    val size = dp(40).coerceIn(1, 192)
    val key = packageName + ":" + size
    view.tag = key
    view.setImageDrawable(null)
    iconCache.get(key)?.let { view.setImageBitmap(it); return }
    val request = uiScope.launch {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationIcon(packageName).toBitmap(size, size) }.getOrNull()
        } ?: return@launch
        iconCache.put(key, bitmap)
        if (!isDestroyed && view.tag == key) view.setImageBitmap(bitmap)
    }
    view.setTag(com.SplashScreenAdvanced.xposedmodule.R.id.native_icon_job, request)
}

internal fun clearAppIconCache() { iconCache.evictAll() }
