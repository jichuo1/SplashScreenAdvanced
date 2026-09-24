package com.SplashScreenAdvanced.xposedmodule.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.HookManager
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.IconHookHandler.getActivityIconOrApp
import com.SplashScreenAdvanced.xposedmodule.hook.utils.ReflectCache
import com.SplashScreenAdvanced.xposedmodule.hook.utils.getValueFrom
import com.SplashScreenAdvanced.xposedmodule.hook.utils.setValueTo
import com.SplashScreenAdvanced.xposedmodule.hook.utils.toTyped
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.toClass
import com.highcapable.kavaref.extension.toClassOrNull

/**
 * 用于从小米桌面检索大图标的辅助类
 *
 * 仅在首次调用 hasLargeIcon / getLargeIconSize / getLargeIconDrawable 时才触发
 */
class XiaomiIconsHelper(private val context: Context, private val classLoader: ClassLoader) {
    private val miuiHomeContext by lazy {
        context.createPackageContext(
            "com.miui.home",
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        )
    }

    private val largeIconsHelperClazz by lazy {
        "com.miui.maml.util.LargeIconsHelper".toClass(loader = miuiHomeContext.classLoader)
    }

    private val dependencyClazz by lazy {
        "com.miui.systemui.MiuiDependency".toClassOrNull(loader = classLoader)
            ?: "com.android.systemui.Dependency".toClassOrNull(loader = classLoader)
    }

    private val mDependencyGet by lazy {
        dependencyClazz?.resolve()?.optional()?.firstMethodOrNull {
            name = "get"
            parameterCount = 1
            parameters(Class::class)
        }?.self
    }

    private val interfacesImplManagerClazz by lazy {
        "com.miui.systemui.interfacesmanager.InterfacesImplManager".toClassOrNull(loader = classLoader)
    }

    private val mImplManagerGet by lazy {
        interfacesImplManagerClazz?.resolve()?.optional()?.firstMethodOrNull {
            name = "getImpl"
            parameterCount = 1
            parameters(Class::class)
        }?.self
    }

    private val appIconsManagerClazz by lazy {
        "com.miui.systemui.graphics.AppIconsManager".toClass(loader = classLoader)
    }

    private val loadAppIcon by lazy {
        appIconsManagerClazz.getDeclaredMethod(
            "loadAppIcon",
            classOf<String>(), classOf<Int>(), classOf<ApplicationInfo>(), classOf<PackageManager>()
        )
    }

    /**
     * AOSP 风格 Dependency 的第三种解析形态
     *
     * `MiuiDependency.get(Class)` 在 HyperOS 2+ 的部分构建中被移除, 而 AOSP 形态的
     * `Dependency` 只剩 `sDependency` 静态字段 + `getDependencyInner(Class)` 实例方法
     * (customiuizer 等模块即用此路径)。返回值是 (方法, sDependency 实例) 对。
     */
    private val aospDependencyGetInner by lazy {
        runCatching {
            val dep = "com.android.systemui.Dependency".toClassOrNull(loader = classLoader)
                ?: return@runCatching null
            val sDep = dep.getDeclaredField("sDependency").apply { isAccessible = true }.get(null)
                ?: return@runCatching null
            sDep.javaClass.getDeclaredMethod("getDependencyInner", Class::class.java)
                .apply { isAccessible = true } to sDep
        }.getOrNull()
    }

    private val appIconsManager by lazy {
        mDependencyGet?.invoke(null, appIconsManagerClazz)
            ?: mImplManagerGet?.invoke(null, appIconsManagerClazz)
            ?: aospDependencyGetInner?.let { (method, sDep) ->
                runCatching { method.invoke(sDep, appIconsManagerClazz) }.getOrNull()
            }
    }

    /**
     * MIUI 框架级主题图标 API
     *
     * `miui.content.res.IconCustomizer` 在 miui-framework 中, 随 boot classpath 进入 SystemUI
     * 进程, MIUI 时代至 HyperOS 各版本均存在(boundo / Perfect-Icons / WOMMO 等模块在用)。
     * 它是 AppIconsManager 失效时最稳的主题图标兜底。
     */
    private val iconCustomizerClazz by lazy {
        "miui.content.res.IconCustomizer".toClassOrNull(loader = classLoader)
    }

    private val getCustomizedIconMethod by lazy {
        runCatching {
            iconCustomizerClazz?.getDeclaredMethod(
                "getCustomizedIcon",
                Context::class.java, String::class.java, String::class.java, Drawable::class.java
            )?.apply { isAccessible = true }
        }.getOrNull()
    }

    private val launcherApps by lazy {
        runCatching { context.getSystemService(LauncherApps::class.java) }.getOrNull()
    }

    /** `UserHandle.of(int)` 是 @SystemApi 隐藏方法, 反射一次缓存 */
    private val userHandleOf by lazy {
        runCatching {
            UserHandle::class.java.getDeclaredMethod("of", Int::class.java).apply { isAccessible = true }
        }.getOrNull()
    }

    private val drawableUtilsClazz by lazy {
        "com.miui.utils.DrawableUtils".toClassOrNull(loader = classLoader)
    }

    private val getFancyChildOrSelf by lazy {
        drawableUtilsClazz?.resolve()?.optional()?.firstMethodOrNull {
            name = "getFancyChildOrSelf"
            parameterCount = 2
            parameters(Drawable::class, Boolean::class)
        }?.self
    }

    /** 当前是否启用 MIUI 完美图标 */
    val isSupportMIUIModeIcon by lazy {
        // HyperOS 4 上 key_miui_mod_icon_enable 与 DrawableUtils 任一缺失都不应判负:
        // IconCustomizer 是框架级主题图标入口, 它存在即代表主题图标机制可用
        Settings.System.getInt(context.contentResolver, "key_miui_mod_icon_enable", 0) == 1 ||
                getFancyChildOrSelf != null || iconCustomizerClazz != null
    }

    // 以下成员进程内恒定, 解析一次复用; hasLargeIcon / getLargeIconSize / getLargeIconDrawable
    // 每次应用启动都会调用, 不能在方法体内重复做全表反射扫描

    private val userHandleCurrent by lazy {
        classOf<UserHandle>().resolve().firstField { name = "CURRENT" }.getValueFrom<UserHandle, Any>(null)
    }

    private val hasLargeIconMethod by lazy {
        largeIconsHelperClazz.resolve().optional().firstMethodOrNull {
            name = "hasLargeIcon"
            parameters(String::class, String::class, String::class, UserHandle::class)
        }?.toTyped<Boolean>()
    }

    private val getLargeIconConfigFileMethod by lazy {
        largeIconsHelperClazz.resolve().optional().firstMethodOrNull {
            name = "getLargeIconConfigFile"
            parameters(String::class, Boolean::class)
        }?.toTyped<Any>()
    }

    private val getLargeIconDrawableMethod by lazy {
        largeIconsHelperClazz.resolve().optional().firstMethodOrNull {
            name = "getLargeIconDrawable"
            parameters(Context::class, String::class, String::class, String::class, String::class, Long::class, UserHandle::class)
        }?.toTyped<Any>()
    }

    private val sManagerListField by lazy {
        largeIconsHelperClazz.resolve().optional().firstFieldOrNull { name = "sManagerList" }
    }

    private val hasLargeIconCache = ConcurrentHashMap<String, Boolean>()
    private val largeIconSizeCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var largeIconConfigLoadedAt = 0L

    @Volatile
    private var hooksInstalled = false

    /** 保护 [ensureHooksInstalled] 的「检查 - 安装」序列 */
    private val installLock = Any()

    /**
     * 安装依赖小米桌面的辅助 Hook (仅首次调用时执行)
     *
     * 必须加锁: hasLargeIcon / getLargeIconSize / getLargeIconDrawable 可能被并发调用, 而这里
     * 每次都会 new 一批 [HookManager]。单纯的 `if (flag) return; flag = true` 在并发下会重复
     * 安装 trampoline, 且这些 HookManager 是局部变量, 装上之后没有句柄可以卸载
     */
    @SuppressLint("DiscouragedApi")
    private fun ensureHooksInstalled() {
        synchronized(installLock) {
            if (hooksInstalled) return
            hooksInstalled = true
            try {
                // 防止获取到 System UI 的 Resources
                HookManager(true) {
                    "miuix.pickerwidget.date.CalendarFormatSymbols".toClassOrNull(loader = miuiHomeContext.classLoader)
                        ?.resolve()?.optional()?.firstMethodOrNull { name = "getWeekDays" }?.self
                }.addReplaceHook({ true }) {
                    val resources = miuiHomeContext.resources
                    val id = resources.getIdentifier("week_days", "array", "com.miui.home")
                    resources.getStringArray(id)
                }.startHook(SystemUIHooker.module)

                // 为获取完美图标时设置一个缓存时间, 避免获取费时图标(如天气)时, 经常显示不出数据的问题 原调用为固定值 0.
                HookManager(true) {
                    "com.miui.maml.util.AppIconsHelper".toClassOrNull(loader = miuiHomeContext.classLoader)
                        ?.resolve()?.optional()?.firstMethodOrNull { name = "getFancyIconDrawable" }?.self
                }.addBeforeHook({ true }) {
                    val packageName = args(args.indexOfFirst { it is String }).string()
                    val cacheTimeIndex = args.indexOfFirst { it is Long }
                    args(cacheTimeIndex).set(getCacheTime(packageName))
                }.startHook(SystemUIHooker.module)

                // 只获取本地天气数据, 不获取网络数据; 参考 https://zhuti.designer.xiaomi.com/docs/blog/weatherApi.html
                HookManager(true) {
                    "com.miui.maml.data.ContentProviderBinder".toClassOrNull(loader = miuiHomeContext.classLoader)
                        ?.resolve()?.optional()?.firstMethodOrNull { name = "getUriText" }?.self
                }.addAfterHook({ true }) {
                    if (result == "content://weather/actualWeatherData/1")
                        result = "content://weather/actualWeatherData/2"
                }.startHook(SystemUIHooker.module)
            } catch (t: Throwable) {
                XMLog.e(t, "MIUIIconsHelper")
            }
        }
    }

    /**
     * 检查给定的程序是否存在大图标
     *
     * @param packageName 要检查的程序包的名称。
     * @return 如果程序包有大图标则返回 `true`，否则返回 `false`。
     */
    fun hasLargeIcon(packageName: String) = try {
        refreshLargeIconConfigIfStale()
        hasLargeIconCache.getOrPut(packageName) {
            ensureHooksInstalled()
            hasLargeIconMethod?.invoke(null, packageName, null, "desktop", userHandleCurrent) ?: false
        }
    } catch (e: Throwable) {
        XMLog.e(t = e) { "Failed to get hasLargeIcon for package $packageName" }
        false
    }

    /**
     * 获取指定程序包的大图标的尺寸。
     *
     * @param packageName 需要获取大图标尺寸的程序包的名称。
     * @return 如果成功获取大图标的尺寸则返回该尺寸，否则在捕获异常后返回null。
     */
    fun getLargeIconSize(packageName: String) = try {
        refreshLargeIconConfigIfStale()
        largeIconSizeCache[packageName] ?: run {
            ensureHooksInstalled()
            val iconsConfigs = getLargeIconConfigFileMethod?.invoke(null, "desktop", false)?.let { configFile ->
                ReflectCache.invokeMethod<HashMap<String, Any>>(configFile, "getIconsConfigs")
            }
            val size = iconsConfigs?.get(packageName)?.let { config ->
                ReflectCache.getField<String>(config, "size")
            }
            if (size != null) largeIconSizeCache[packageName] = size
            size
        }
    } catch (e: Throwable) {
        XMLog.e(t = e) { "Failed to get large icon size for package $packageName" }
        null
    }

    private fun refreshLargeIconConfigIfStale() {
        val now = SystemClock.elapsedRealtime()
        if (now - largeIconConfigLoadedAt < LARGE_ICON_CONFIG_TTL_MS) return
        synchronized(installLock) {
            val innerNow = SystemClock.elapsedRealtime()
            if (innerNow - largeIconConfigLoadedAt < LARGE_ICON_CONFIG_TTL_MS) return
            sManagerListField?.setValueTo(null, null)
            hasLargeIconCache.clear()
            largeIconSizeCache.clear()
            largeIconConfigLoadedAt = innerNow
        }
    }

    /**
     * 获取指定包名的大图标
     *
     * @param packageName 应用程序的包名
     * @return 原始大图标可绘制对象，如果未找到则为 null
     */
    fun getLargeIconDrawable(packageName: String) = try {
        ensureHooksInstalled()
        getLargeIconDrawableMethod?.invoke(
            null, miuiHomeContext, packageName, null, "desktop", null, 0L, userHandleCurrent
        )?.let { largeIcon ->
            ReflectCache.invokeMethod<Drawable>(largeIcon, "getDrawable")
        }
    } catch (e: Throwable) {
        XMLog.e(t = e) { "Failed to get large icon drawable for package $packageName" }
        null
    }

    /**
     * 从指定的应用程序包中获取完美的图标 Drawable
     *
     * HyperOS 4 上 `AppIconsManager`/`loadAppIcon` 任一环节(类缺失、签名变更、DI 解析失败)
     * 都会静默落到原始图标, 因此按可用性逐层回退:
     * AppIconsManager -> IconCustomizer -> LauncherActivityInfo.getIcon(0) -> 原图标
     *
     * @param packageName 要获取图标的应用程序包的包名。
     * @param userId 应用程序的用户 ID。
     * @param applicationInfo 应用程序的 ApplicationInfo 对象。
     * @param className 当前启动 Activity 的类名, 用于 IconCustomizer 的组件级图标名解析。
     * @return 如果成功获取到完美图标，则返回一个 BitmapDrawable；如果发生错误，则回退到默认方式获取。
     */
    fun getFancyIconDrawable(
        packageName: String,
        userId: Int,
        applicationInfo: ApplicationInfo?,
        className: String? = null
    ): Drawable? {
        val pm = SystemUIHooker.appContext?.packageManager ?: return null
        // original 只在下层兜底真正需要时才求值: loadAppIcon 命中的健康路径上
        // 不应为拿不到的回退值白付一次 PM 图标加载 (binder + 解码)
        val original by lazy(LazyThreadSafetyMode.NONE) {
            runCatching { getActivityIconOrApp(pm) }.getOrNull()
        }

        // 1. MIUI SystemUI 内置完美图标
        runCatching {
            loadAppIcon.invoke(appIconsManager, packageName, userId, applicationInfo, pm) as? Drawable
        }.getOrNull()?.let {
            XMLog.i { "getFancyIconDrawable: AppIconsManager.loadAppIcon hit for $packageName" }
            return it
        }

        // 2. 框架级主题图标: IconCustomizer.getCustomizedIcon(context, pkg, className, original)
        runCatching {
            getCustomizedIconMethod?.invoke(null, context, packageName, className, original) as? Drawable
        }.getOrNull()?.let {
            XMLog.i { "getFancyIconDrawable: IconCustomizer hit for $packageName/$className" }
            return it
        }

        // 3. LauncherActivityInfo.getIcon(0): density=0 时 PackageManager 返回主题图标
        getThemedIconViaLauncherApps(packageName, userId)?.let {
            XMLog.i { "getFancyIconDrawable: LauncherActivityInfo.getIcon(0) hit for $packageName" }
            return it
        }

        XMLog.w { "getFancyIconDrawable: all themed-icon layers missed for $packageName, fallback to original" }
        return original
    }

    /**
     * 经 `LauncherActivityInfo.getIcon(0)` 取主题图标 (ROM 无关)
     *
     * density=0 时 PackageManager 走主题资源解析, 指定具体 DPI 反而绕过主题——
     * liyafe1997/AlwaysThemedIcon 在 MIUI 与 Flyme 上实测有效; 对无主题机制的 ROM
     * (原生 AOSP/ColorOS/OriginOS/OneUI) 返回的等价于原图标, 调用方把它当
     * "主题图标或原始图标"使用即可, 是无害的一次性 binder 调用。
     *
     * @param packageName 目标应用包名
     * @param userId 目标应用所在 user (work profile 需传真实 userId, 不能用 SystemUI 自身的)
     * @return 主题图标或原始图标; 无 launcher activity / 反射失败时 null
     */
    fun getThemedIconViaLauncherApps(packageName: String, userId: Int): Drawable? = runCatching {
        val handle = userHandleOf?.invoke(null, userId) as? UserHandle
        launcherApps?.getActivityList(packageName, handle ?: android.os.Process.myUserHandle())
            ?.firstOrNull()?.getIcon(0)
    }.getOrNull()

    /**
     * 返回给定包名的缓存时间。
     *
     * @param packageName 要获取缓存时间的包名。
     * @return 缓存时间(毫秒)。
     */
    private fun getCacheTime(packageName: String) = when (packageName) {
        "com.miui.weather2" -> 3600000L
        "com.android.deskclock" -> 0L
        else -> 86400000L
    }

    companion object {
        private const val LARGE_ICON_CONFIG_TTL_MS = 60_000L
    }
}
