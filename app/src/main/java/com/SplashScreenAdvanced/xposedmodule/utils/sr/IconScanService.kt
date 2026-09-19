package com.SplashScreenAdvanced.xposedmodule.utils.sr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.utils.enhance.IconEnhanceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * 离线超分工厂的扫描服务
 *
 * 在**用户主动发起**的前提下, 遍历已安装应用 → 提取高分辨率图标 → 离线增强 → 写入缓存。
 * 这样启动路径上只剩"查索引 + 解码", 把 AI/重算这类耗时操作的代价从关键路径彻底移走。
 *
 * 关键实现取舍:
 * - **前台服务**: 批量处理可能持续数十秒, 普通后台任务会被系统冻结; 用 `dataSync` 类型前台服务
 *   保证可完成, 同时用通知展示进度, 用户随时可见、可取消;
 * - **跳过已缓存**: 通过 `versionCode + targetSize` 判定, 应用未更新且目标尺寸未变时不重复处理,
 *   因此"再次扫描"是增量的;
 * - **高密度加载**: 用 `getDrawableForDensity` 按更高密度取图标, 拿到资源包里最大的那一份,
 *   避免一开始就丢分辨率; 失败回退到 `loadIcon`;
 * - **单个应用失败即跳过**: 任何一个应用处理失败都不应中断整轮扫描。
 */
class IconScanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(0, 0, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        scope.launch {
            runCatching { runScan() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 扫描主体

    private suspend fun runScan() {
        val pm = packageManager
        val target = targetIconSizePx()
        // 以两倍目标尺寸去请求资源: 资源包里通常有更高密度的一份, 取到后再由引擎收敛
        val requestDensity = (target * 2).coerceIn(320, 960)

        // 用 getInstalledPackages 而非 getInstalledApplications: 版本号(longVersionCode)在
        // PackageInfo 上, 后者需要为每个应用再单独查询一次, 百来个应用就是百来次额外 IPC
        val packages: List<PackageInfo> = runCatching {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        }.getOrDefault(emptyList())
        val total = packages.size

        val index = IconCacheStore.readIndex(this)
        var scanned = 0
        var processed = 0
        var lastNotifyAt = 0L

        for (packageInfo in packages) {
            if (!currentCoroutineContext().isActive) break
            scanned++
            val app = packageInfo.applicationInfo ?: continue
            val pkg = packageInfo.packageName
            if (pkg == packageName) continue   // 跳过模块自身

            val cached = index.entries[pkg]
            val upToDate = cached != null &&
                    cached.versionCode == packageInfo.longVersionCode &&
                    cached.targetSize == target
            if (upToDate) {
                processed++
            } else {
                val drawable = loadIcon(pm, app, requestDensity)
                val bitmap = drawable?.let { IconEnhanceEngine.enhanceOffline(it, target) }
                val bytes = bitmap?.let { encodeLosslessWebp(it) }
                bitmap?.recycle()
                if (bytes != null) {
                    val entry = IconCacheStore.Entry(
                        file = IconCacheStore.fileNameFor(pkg, System.currentTimeMillis()),
                        versionCode = packageInfo.longVersionCode,
                        targetSize = target,
                        createdAt = System.currentTimeMillis(),
                    )
                    // 先落新文件再删旧文件: 反过来的话 put 失败就把已有缓存也弄丢了
                    if (IconCacheStore.put(this, pkg, entry, bytes)) {
                        processed++
                        cached?.let { IconCacheStore.fileFor(this, it.file).delete() }
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastNotifyAt > NOTIFY_INTERVAL_MS) {
                lastNotifyAt = now
                notify(buildNotification(scanned, total, pkg))
            }
        }

        notify(buildNotification(scanned, total, null))
    }

    // ---------------------------------------------------------------- 图标加载

    /**
     * 取该应用最高分辨率的图标
     *
     * `getDrawableForDensity` 只影响本次解码, **不会**像 `updateConfiguration` 那样改动共享的
     * `Resources` 状态, 因此没有并发副作用。
     */
    private fun loadIcon(pm: PackageManager, app: ApplicationInfo, densityDpi: Int): Drawable? {
        if (app.icon != 0) {
            runCatching {
                val res = pm.getResourcesForApplication(app)
                res.getDrawableForDensity(app.icon, densityDpi, null)
            }.getOrNull()?.let { return it }
        }
        return runCatching { app.loadIcon(pm) }.getOrNull()
    }

    /**
     * 目标图标边长
     *
     * 宿主用 `starting_surface_icon_size`(AOSP 为 160dp) 作为最终绘制尺寸; 这里从 framework-res
     * 读取同一资源, 保证离线产物的尺寸与运行期一致（不一致会被 Provider 的尺寸校验拦下, 只是
     * 退回实时路径而不会出错）。
     */
    private fun targetIconSizePx(): Int {
        val res = Resources.getSystem()
        val id = res.getIdentifier("starting_surface_icon_size", "dimen", "android")
        val px = if (id != 0) {
            res.getDimensionPixelSize(id)
        } else {
            (DEFAULT_ICON_DP * res.displayMetrics.density).toInt()
        }
        return px.coerceIn(64, 1024)
    }

    /** WebP **无损**编码: 有损会引入新伪影, 与提升画质的目标相悖 */
    private fun encodeLosslessWebp(bitmap: Bitmap): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
            out.toByteArray()
        }
    }.getOrNull()

    // ---------------------------------------------------------------- 通知

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sr_scan_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(done: Int, total: Int, pkg: String?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.sr_scan_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (total > 0) {
            builder.setContentText(
                getString(R.string.sr_scan_progress, done, total) + (pkg?.let { " · $it" } ?: "")
            )
            builder.setProgress(total, done, false)
        } else {
            builder.setContentText(getString(R.string.sr_scan_preparing))
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun notify(notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "sr_scan"
        private const val NOTIFICATION_ID = 0x5301
        private const val NOTIFY_INTERVAL_MS = 200L
        private const val DEFAULT_ICON_DP = 160

        fun start(context: android.content.Context) {
            runCatching {
                context.startForegroundService(Intent(context, IconScanService::class.java))
            }
        }
    }
}
