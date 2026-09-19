package com.SplashScreenAdvanced.xposedmodule.hook.utils

import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import com.highcapable.kavaref.extension.toClassOrNull
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.result.ClassData
import java.io.File
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 宿主类查找：先按稳定 AOSP/OEM 类名，再读上次 DexKit 缓存，最后才打开 native 扫 dex。
 *
 * SystemUI 启动链路上绝大多数 ROM 类名仍然能对上，这时不会加载 `libdexkit.so`。
 * 只有类名对不上（混淆 / 改包）时才付扫描成本，并把真实类名写进宿主 cacheDir，下次直接 Class.forName。
 */
object HostDexLookup {
    private val nativeLoaded = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    private var loader: ClassLoader? = null

    @Volatile
    private var apkPath: String? = null

    @Volatile
    private var cache: StampCache? = null

    @Volatile
    private var bridge: DexKitBridge? = null

    fun attach(classLoader: ClassLoader, cacheDir: File?, apkPath: String?) {
        synchronized(lock) {
            closeBridgeLocked()
            this.loader = classLoader
            this.apkPath = apkPath?.takeIf { File(it).isFile }
            val stamp = buildStamp(this.apkPath)
            this.cache = cacheDir?.let { StampCache(File(it, "ssa_dexkit.properties"), stamp) }
        }
    }

    fun closeBridge() {
        synchronized(lock) { closeBridgeLocked() }
    }

    /**
     * 按 [names] 依次 [Class.forName]，失败则用缓存里的真实类名，再失败才跑 [query]。
     */
    fun findClass(vararg names: String, query: (DexKitBridge.() -> ClassData?)? = null): Class<*>? {
        val cl = loader
        if (cl == null) {
            XMLog.w { "HostDexLookup.findClass before attach: ${names.joinToString()}" }
            return null
        }
        names.forEach { name ->
            name.toClassOrNull(loader = cl)?.let { return it }
        }
        val cacheKey = names.firstOrNull() ?: return null
        cache?.get(cacheKey)?.toClassOrNull(loader = cl)?.let { return it }
        if (query == null) return null
        return synchronized(lock) {
            names.forEach { name ->
                name.toClassOrNull(loader = cl)?.let { return@synchronized it }
            }
            cache?.get(cacheKey)?.toClassOrNull(loader = cl)?.let { return@synchronized it }
            val data = runCatching { openBridgeLocked()?.query() }
                .onFailure { XMLog.e(t = it) { "DexKit query failed for $cacheKey" } }
                .getOrNull()
                ?: return@synchronized null
            cache?.put(cacheKey, data.name)
            runCatching { data.getInstance(cl) }
                .onFailure { XMLog.e(t = it) { "DexKit class load failed: ${data.name}" } }
                .getOrNull()
        }
    }

    private fun buildStamp(apkPath: String?): String {
        if (apkPath.isNullOrEmpty()) return "unknown"
        val file = File(apkPath)
        return "${file.length()}:${file.lastModified()}"
    }

    private fun ensureNativeLocked(): Boolean {
        if (nativeLoaded.get()) return true
        return runCatching {
            System.loadLibrary("dexkit")
            nativeLoaded.set(true)
            true
        }.getOrElse {
            XMLog.e(t = it) { "libdexkit.so failed to load" }
            false
        }
    }

    private fun openBridgeLocked(): DexKitBridge? {
        bridge?.let { return it }
        if (!ensureNativeLocked()) return null
        val currentLoader = loader
        if (currentLoader != null) {
            runCatching { DexKitBridge.create(currentLoader, true) }
                .onSuccess { created ->
                    bridge = created
                    return created
                }
                .onFailure { XMLog.w { "DexKit create(classLoader) failed: ${it.message}" } }
        }
        val path = apkPath
        if (!path.isNullOrEmpty()) {
            runCatching { DexKitBridge.create(path) }
                .onSuccess { created ->
                    bridge = created
                    return created
                }
                .onFailure { XMLog.e(t = it) { "DexKit create(apk) failed" } }
        }
        return null
    }

    private fun closeBridgeLocked() {
        runCatching { bridge?.close() }
        bridge = null
    }
}

internal object DexHostQueries {
    val splashscreenContentDrawer: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell.startingsurface") {
            addMethod { name("makeSplashScreenContentView") }
            addMethod { name("getWindowAttrs"); paramCount(2) }
        } ?: firstClass {
            addMethod { name("makeSplashScreenContentView") }
            addMethod { name("getWindowAttrs"); paramCount(2) }
        }
    }

    /**
     * 图标栅格化尺寸修正的兜底查询
     *
     * 目标是 SplashscreenIconDrawableFactory$ImmobileIconDrawable —— adaptive / 非 adaptive /
     * 自带 splash icon 三条分支的唯一汇合点。特征方法是私有的 preDrawIcon(Drawable, int);
     * 这里刻意不加 paramCount 约束: 该方法名在 AOSP 中唯一, 放宽签名可在 ROM 改动参数时仍然命中,
     * 真正需要的只是"定位到类", 构造解析由调用侧完成。
     */
    val immobileIconDrawable: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell.startingsurface") {
            addMethod { name("preDrawIcon") }
        } ?: firstClass {
            addMethod { name("preDrawIcon") }
            className("IconDrawable", StringMatchType.Contains)
        }
    }

    fun startingWindowViewBuilder(outerName: String?): DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell.startingsurface") {
            if (outerName != null) className(outerName + "$", StringMatchType.StartsWith)
            addMethod { name("createIconDrawable") }
        } ?: firstClass {
            addMethod { name("createIconDrawable") }
            className("ViewBuilder", StringMatchType.Contains)
        }
    }

    fun iconColor(outerName: String?): DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell.startingsurface") {
            if (outerName != null) className(outerName + "$", StringMatchType.StartsWith)
            className("IconColor", StringMatchType.Contains)
        }
    }

    val baseIconFactory: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.launcher3.icons") {
            addMethod { name("normalizeAndWrapToAdaptiveIcon") }
            addMethod { name("createIconBitmap") }
        }
    }

    val iconProvider: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.launcher3.icons") {
            addMethod { name("getIcon"); paramCount(2) }
        }
    }

    val shellTaskOrganizer: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell") {
            addMethod { name("removeStartingWindow") }
        }
    }

    val oplusStartingWindowManager: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.wm.shell.startingsurface") {
            addMethod { name("setContentViewBackground") }
            addMethod { name("getWindowAttrsIfPresent") }
        } ?: firstClass {
            addMethod { name("setContentViewBackground") }
            addMethod { name("getWindowAttrsIfPresent") }
        }
    }

    val activityRecord: DexKitBridge.() -> ClassData? = {
        firstClass("com.android.server.wm") {
            addMethod { name("validateStartingWindowTheme"); paramCount(3) }
            addMethod { name("showStartingWindow"); paramCount(7) }
        }
    }
}

private fun DexKitBridge.firstClass(
    vararg packages: String,
    matcher: ClassMatcher.() -> Unit,
): ClassData? {
    val result = findClass {
        if (packages.isNotEmpty()) searchPackages(*packages)
        matcher(matcher)
    }
    if (result.isEmpty()) return null
    if (result.size > 1) {
        XMLog.w { "DexKit matched ${result.size} classes, using ${result[0].name}" }
    }
    return result[0]
}

private class StampCache(private val file: File, stamp: String) {
    private val props = Properties()
    private val lock = Any()

    init {
        if (file.exists()) {
            runCatching { file.reader().use { props.load(it) } }
                .onFailure { XMLog.w { "DexKit cache load failed: ${it.message}" } }
        }
        if (props.getProperty(STAMP_KEY) != stamp) {
            props.clear()
            props.setProperty(STAMP_KEY, stamp)
        }
    }

    fun get(key: String): String? = synchronized(lock) { props.getProperty(key) }

    fun put(key: String, value: String) {
        synchronized(lock) {
            props.setProperty(key, value)
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writer().use { props.store(it, "SplashScreenAdvanced DexKit class cache") }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }.onFailure { XMLog.w { "DexKit cache save failed: ${it.message}" } }
        }
    }

    private companion object {
        const val STAMP_KEY = "__stamp"
    }
}
