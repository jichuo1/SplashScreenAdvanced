package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 应用列表页共用的应用基础信息
 *
 * 不含图标 —— 图标由 [rememberAppIcon] 在行被组合时按需加载
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

/**
 * 缓存有效期
 *
 * 取一分钟是个折中: 用户在几个列表页之间来回切换通常就在这个量级内, 而装/卸应用之后最多
 * 一分钟内看到的还是旧列表, 退出重进即可刷新。不做包变化广播监听是为了不引入常驻接收器
 */
private const val CACHE_TTL_MS = 60_000L

private val loadMutex = Mutex()

@Volatile
private var cache: List<InstalledApp>? = null

@Volatile
private var cachedAt = 0L

private fun validCache(): List<InstalledApp>? =
    cache?.takeIf { SystemClock.elapsedRealtime() - cachedAt < CACHE_TTL_MS }

/**
 * 读取已安装应用的基础信息, 带进程级短期缓存
 *
 * CustomScope / BgIndividual / MinDuration 等页面原先各自跑一遍
 * `getInstalledApplications()` + 对每个应用 `loadLabel()` —— 几百次 binder 调用,
 * 每进一个列表页重来一次。这里统一收口并共享结果。
 *
 * 调用方可以直接在协程里调用, 内部已切到 IO。
 */
suspend fun loadInstalledApps(context: Context): List<InstalledApp> {
    validCache()?.let { return it }

    return loadMutex.withLock {
        // 双检: 等锁期间可能已经有别的调用把缓存填好了
        validCache() ?: withContext(Dispatchers.IO) {
            val pm = context.applicationContext.packageManager
            val apps = pm.getInstalledApplications(0)
            coroutineScope {
                apps.map { appInfo ->
                    async {
                        InstalledApp(
                            packageName = appInfo.packageName,
                            appName = appInfo.loadLabel(pm).toString(),
                            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                        )
                    }
                }.awaitAll()
            }
        }.also {
            cache = it
            cachedAt = SystemClock.elapsedRealtime()
        }
    }
}
