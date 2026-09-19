package com.SplashScreenAdvanced.xposedmodule.utils.sr

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * 离线超分缓存的跨进程读取通道
 *
 * ## 为什么必须是 ContentProvider
 *
 * SystemUI 虽然运行在 `system_app` 域, 但 **SELinux 不允许它直接读取本模块 App 私有目录下的
 * `app_data_file`**。共享文件那条路走不通, 而 ContentProvider 走标准 Binder + 权限模型,
 * 是唯一不依赖 root 或 permissive 的可靠通道。
 *
 * ## 权限与暴露面
 *
 * `exported=true` 且不加自定义权限: 缓存内容就是应用图标这一**公开信息**, 泄露风险可忽略;
 * 反过来若加签名级权限, SystemUI 与模块签名不同反而读不到。只读实现（不提供 insert/update/
 * delete）进一步收窄了暴露面。
 *
 * ## 使用方式
 *
 * SystemUI 侧用 `openFileDescriptor(uri, "r")` 拿到 FD 后直接解码, 全程零拷贝:
 * ```
 * content://<AUTHORITY>/icon/<packageName>?size=<targetPx>
 * ```
 * `size` 参与校验: 缓存是按特定目标尺寸生成的, 若运行期尺寸不同（例如用户改了图标缩放）
 * 则应视为未命中, 由调用方回退到实时路径。
 */
class IconCacheProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.SplashScreenAdvanced.xposedmodule.iconcache"
        private const val PATH_ICON = "icon"
        private const val CODE_ICON = 1

        fun uriFor(packageName: String, targetSize: Int): Uri =
            Uri.parse("content://$AUTHORITY/$PATH_ICON/$packageName").buildUpon()
                .appendQueryParameter("size", targetSize.toString())
                .build()
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("provider not attached")
        val pkg = uri.lastPathSegment ?: throw FileNotFoundException("missing package")
        val requestedSize = uri.getQueryParameter("size")?.toIntOrNull()

        val entry = IconCacheStore.readIndex(ctx).entries[pkg]
            ?: throw FileNotFoundException("no cache for $pkg")
        if (requestedSize != null && entry.targetSize != requestedSize) {
            throw FileNotFoundException("size mismatch for $pkg")
        }
        val file = IconCacheStore.fileFor(ctx, entry.file)
        if (!file.isFile) throw FileNotFoundException("cache file missing for $pkg")

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/webp"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
