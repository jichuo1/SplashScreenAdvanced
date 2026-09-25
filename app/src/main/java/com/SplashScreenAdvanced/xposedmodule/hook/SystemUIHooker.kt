package com.SplashScreenAdvanced.xposedmodule.hook

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import androidx.annotation.Keep
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker.init
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker.onHook
import com.SplashScreenAdvanced.xposedmodule.hook.base.HookManager
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.BgHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.BottomHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.IconHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.OplusHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.ScopeHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.XiaomiHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.utils.DexHostQueries
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.loadHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HostDexLookup
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils.isColorOS
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils.isHyperOS
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.makeAccessible
import com.highcapable.kavaref.extension.toClass
import com.highcapable.kavaref.extension.toClassOrNull
import io.github.libxposed.api.XposedModule

/**
 * SystemUI 进程内的 Hook 入口
 */
object SystemUIHooker {

    lateinit var module: XposedModule
        private set

    lateinit var classLoader: ClassLoader
        private set

    /** 宿主（SystemUI）Application 的 Context */
    @Volatile
    var appContext: Context? = null
        private set

    /** 当前用户 ID */
    val appUserId: Int = android.os.Process.myUid() / 100000

    @Keep
    object Members {
        // 宿主类在进程内恒定, 这里解析一次复用。
        // 原先每个成员各自 toClassOrNull 一遍, SplashscreenContentDrawer 被查了 5 次、
        // BaseIconFactory 2 次, 而 A14 的 Builder 回退每次都要先吃一个 ClassNotFoundException——
        // 这些都发生在 SystemUI attachBaseContext 的启动关键路径上。
        // 注意: 必须声明在下面各 HookManager 之前, 对象初始化按声明顺序执行
        private val splashscreenContentDrawerClass by lazy {
            HostDexLookup.findClass(
                "com.android.wm.shell.startingsurface.SplashscreenContentDrawer",
                query = DexHostQueries.splashscreenContentDrawer,
            )
        }

        /** A15+ 为 StartingWindowViewBuilder, A14 为 SplashViewBuilder */
        private val startingWindowViewBuilderClass by lazy {
            val outerName = splashscreenContentDrawerClass?.name
            HostDexLookup.findClass(
                $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$StartingWindowViewBuilder",
                $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$SplashViewBuilder",
                *(outerName?.let {
                    arrayOf(
                        "$it\$StartingWindowViewBuilder",
                        "$it\$SplashViewBuilder",
                    )
                } ?: emptyArray<String>()),
                query = DexHostQueries.startingWindowViewBuilder(outerName),
            )
        }

        private val baseIconFactoryClass by lazy {
            HostDexLookup.findClass(
                "com.android.launcher3.icons.BaseIconFactory",
                query = DexHostQueries.baseIconFactory,
            )
        }

        private val oplusStartingWindowManagerClass by lazy {
            HostDexLookup.findClass(
                "com.android.wm.shell.startingsurface.OplusShellStartingWindowManager",
                query = DexHostQueries.oplusStartingWindowManager,
            )
        }

        val makeSplashScreenContentView = HookManager {
            splashscreenContentDrawerClass
                ?.resolve()?.optional()?.let { resolver ->
                    // 优先匹配参数含 ActivityInfo/StartingWindowInfo 的重载:
                    // ROM 若加了同名重载, 无条件 firstMethodOrNull 可能钩到错误签名
                    resolver.firstMethodOrNull {
                        name = "makeSplashScreenContentView"
                        parameters { types ->
                            types.any {
                                it == classOf<ActivityInfo>() || it.name.endsWith("StartingWindowInfo")
                            }
                        }
                    } ?: resolver.firstMethodOrNull { name = "makeSplashScreenContentView" }
                }?.self
        }
        val getWindowAttrs = HookManager {
            splashscreenContentDrawerClass
                ?.resolve()?.optional()?.firstMethodOrNull {
                    name = "getWindowAttrs"
                    parameterCount = 2
                }?.self
        }
        val getBGColorFromCache = HookManager {
            splashscreenContentDrawerClass
                ?.resolve()?.optional()?.firstMethodOrNull {
                    name = "getBGColorFromCache"
                    parameterCount = 2
                }?.self
        }
        val startingWindowViewBuilderConstructor = HookManager {
            startingWindowViewBuilderClass
                ?.resolve()?.optional()?.firstConstructorOrNull { parameterCount { it >= 2 } }?.self
        }
        val createIconDrawable = HookManager {
            startingWindowViewBuilderClass
                ?.resolve()?.optional()?.firstMethodOrNull { name = "createIconDrawable" }?.self
        }
        /**
         * 强制样式落到最终决策点
         *
         * 部分三方 ROM (如 AfterlifeOS) 会把 AOSP 主线的 splash 改动 backport 回来: 在其
         * makeSplashScreenContentView() 中按 canUseIcon() 把 suggestType 重映射为
         * SOLID_COLOR, 而 SplashViewBuilder.build() 见到 SOLID_COLOR 会把 mFinalIconSize
         * 直接置 0 —— 于是"强制开启启动遮罩"只剩纯色、拿不到图标。
         *
         * chooseStyle() 写入的 mSuggestType 是 build() 唯一的判断依据, 改写它即可绕过上游
         * 全部重映射。该方法在 AOSP 14 原生同样存在, 所以这里不做 ROM 区分。
         */
        val chooseStyle_SplashViewBuilder = HookManager {
            startingWindowViewBuilderClass
                ?.resolve()?.optional()?.firstMethodOrNull {
                    name = "chooseStyle"
                    parameterCount = 1
                }?.self
        }
        /** 消除 ROM 的 suggestType → splashType 降级分支; AOSP 14 无此方法, 解析为 null 即不安装 */
        val canUseIcon_SplashscreenContentDrawer = HookManager {
            splashscreenContentDrawerClass
                ?.resolve()?.optional()?.firstMethodOrNull {
                    name = "canUseIcon"
                    parameterCount = 1
                }?.self
        }
        /**
         * 图标栅格化尺寸修正的落点
         *
         * 宿主把 splash 图标先栅格化成 starting_surface_default_icon_size(108dp)、再放大到
         * starting_surface_icon_size(160dp) 绘制(1.48x 位图放大), 且高密度设备上 loadInDetail
         * 恒为 false(SplashscreenContentDrawer$HighResIconProvider 的 currentDpi < DENSITY_XHIGH
         * 判定) —— 这是图标模糊的直接原因, 好在图标源本身通常够清晰(如 108dp@xxxhdpi = 432px
         * 对 160dp@420dpi = 420px)。
         *
         * 该类是 adaptive / 非 adaptive / 自带 splash icon 三条分支的唯一汇合点, 构造签名为
         * (Drawable, int srcIconSize, int iconSize, boolean loadInDetail, Handler preDrawHandler)。
         */
        val immobileIconDrawableConstructor = HookManager {
            HostDexLookup.findClass(
                "com.android.wm.shell.startingsurface.SplashscreenIconDrawableFactory\$ImmobileIconDrawable",
                query = DexHostQueries.immobileIconDrawable,
            )?.resolve()?.optional()?.firstConstructorOrNull()?.self
        }

        /**
         * 图标栅格化的实际执行点
         *
         * 宿主在构造 [ImmobileIconDrawable] 时只是 `preDrawHandler.post(() -> preDrawIcon(...))`,
         * 真正的"把 drawable 画成位图"发生在这里、且**不在主线程**。因此画质增强挂在这里比挂在
         * 构造器上更合适: 构造器处于 `makeSplashScreenContentView` 的同步区间, 在那做 12~40ms 的
         * 重采样会直接推迟启动遮罩的出现; 而此刻拿到的 size 也正是最终绘制尺寸。
         */
        val preDrawIcon_ImmobileIconDrawable = HookManager {
            HostDexLookup.findClass(
                "com.android.wm.shell.startingsurface.SplashscreenIconDrawableFactory\$ImmobileIconDrawable",
                query = DexHostQueries.immobileIconDrawable,
            )?.resolve()?.optional()?.firstMethodOrNull {
                name = "preDrawIcon"
                parameterCount { it >= 2 }
            }?.self
        }
        val iconColor_constructor = HookManager {
            val outerName = splashscreenContentDrawerClass?.name
            HostDexLookup.findClass(
                $$"com.android.wm.shell.startingsurface.SplashscreenContentDrawer$ColorCache$IconColor",
                *(outerName?.let { arrayOf("$it\$ColorCache\$IconColor") } ?: emptyArray<String>()),
                query = DexHostQueries.iconColor(outerName),
            )?.resolve()?.optional()?.firstConstructorOrNull()?.self
        }
        val getIcon_IconProvider = HookManager(!isColorOS) {
            HostDexLookup.findClass(
                "com.android.launcher3.icons.IconProvider",
                query = DexHostQueries.iconProvider,
            )?.resolve()?.optional()?.let { resolver ->
                resolver.firstMethodOrNull {
                    name = "getIcon"
                    parameterCount = 2
                    parameters { types ->
                        Integer.TYPE in types &&
                                (classOf<ActivityInfo>() in types || classOf<ComponentInfo>() in types)
                    }
                } ?: resolver.firstMethodOrNull {
                    // 签名细筛落空时的二级匹配: IconProvider 内所有 getIcon 重载返回的都是
                    // 应用图标 Drawable, 回调里 result as Drawable 失败会被 HookManager 隔离
                    name = "getIcon"
                    parameterCount = 2
                    parameters { types -> Integer.TYPE in types }
                }
            }?.self
        }
        val normalizeAndWrapToAdaptiveIcon = HookManager {
            baseIconFactoryClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "normalizeAndWrapToAdaptiveIcon"
            }?.self
        }
        val createIconBitmap_BaseIconFactory = HookManager {
            baseIconFactoryClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "createIconBitmap"
                parameters(android.graphics.drawable.Drawable::class, Float::class, Int::class)
            }?.self
        }
        val build_SplashScreenViewBuilder = HookManager {
            $$"android.window.SplashScreenView$Builder".toClassOrNull(loader = classLoader)?.resolve()?.optional()?.firstMethodOrNull {
                name = "build"
            }?.self
        }
        val removeStartingWindow = HookManager {
            HostDexLookup.findClass(
                "com.android.wm.shell.ShellTaskOrganizer",
                query = DexHostQueries.shellTaskOrganizer,
            )?.resolve()?.optional()?.firstMethodOrNull {
                name = "removeStartingWindow"
            }?.self
        }

        // Xiaomi
        val isMiuiHome_TaskSnapshotHelperImpl = HookManager(
            isHyperOS && HostDexLookup.findClass("android.app.TaskSnapshotHelperImpl") != null
        ) {
            HostDexLookup.findClass("android.app.TaskSnapshotHelperImpl")?.resolve()?.optional()?.firstMethodOrNull {
                name = "isMiuiHome"
                parameters(String::class)
            }?.self
        }
        val updateForceDarkSplashScreen_ForceDarkHelperStubImpl = HookManager(isHyperOS) {
            $$"android.window.SplashScreenView$Builder".toClassOrNull(loader = classLoader)?.resolve()?.optional()?.firstMethodOrNull {
                name = "isStaringWindowUnderNightMode"
                emptyParameters()
            }?.self ?: "android.view.ForceDarkHelperStubImpl".toClassOrNull(loader = classLoader)?.resolve()?.optional()
                ?.firstMethodOrNull {
                    name = "updateForceDarkSplashScreen"
                }?.self
        }

        // Oplus
        val setContentViewBackground_OplusShellStartingWindowManager = HookManager(isColorOS) {
            oplusStartingWindowManagerClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "setContentViewBackground"
            }?.self
        }
        val getIconExt_OplusShellStartingWindowManager = HookManager(isColorOS) {
            oplusStartingWindowManagerClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "getIconExt"
                parameterCount { it >= 4 }
            }?.self
        }
        val getWindowAttrsIfPresent_OplusShellStartingWindowManager = HookManager(isColorOS) {
            oplusStartingWindowManagerClass?.resolve()?.optional()?.firstMethodOrNull {
                name = "getWindowAttrsIfPresent"
            }?.self
        }
    }

    /** 是否已经完成首次 Hook（attachBaseContext 可能被多次调用，仅首次执行） */
    @Volatile
    private var isHooked = false

    /**
     * 由外部在 SystemUI 进程加载时调用：注入 [module]/[classLoader]，
     * Hook `Application#attachBaseContext` 以捕获宿主 Context 并触发实际 Hook。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        this.module = module
        this.classLoader = classLoader

        val attachHook = HookManager {
            "android.app.Application".toClass(loader = classLoader).resolve().optional().firstMethodOrNull {
                name = "attachBaseContext"
                parameters(Context::class)
                superclass()
            }?.self
        }
        attachHook.addAfterHook({ true }) {
            if (isHooked) return@addAfterHook
            appContext = args(0).any() as? Context
            isHooked = true
            onHook()
            // Context 已捕获、功能 Hook 已安装，此 hook 使命完成，自摘除避免后续空转
            attachHook.unhook()
        }.startHook(module)
        // 非门控：确认 SystemUI 进程内入口已执行、attachBaseContext 目标是否解析成功
        XMLog.i { "[SystemUI] init: attachBaseContext ${if (attachHook.member != null) "resolved" else "UNRESOLVED"}" }
    }

    /**
     * 热重载后由新一代代码调用：复用上一代捕获的宿主 [classLoader] 与 [appContext]，直接重新安装功能 Hook。
     *
     * 热重载不会重放 `attachBaseContext`（宿主 Application 早已创建），因此不能走 [init] 的捕获流程，
     * 需在 classLoader/Context 就绪后直接执行 [onHook]。[Members] 在新一代为全新单例，
     * 首次访问时会基于此处设置的 [classLoader] 解析宿主成员。
     */
    fun reHook(module: XposedModule, classLoader: ClassLoader, appContext: Context?) {
        this.module = module
        this.classLoader = classLoader
        this.appContext = appContext
        isHooked = true
        onHook()
    }

    /** 开始 Hook */
    private fun onHook() {
        HostDexLookup.attach(
            classLoader = classLoader,
            cacheDir = appContext?.cacheDir,
            apkPath = appContext?.applicationInfo?.sourceDir,
        )
        try {
            installHooks()
        } finally {
            HostDexLookup.closeBridge()
        }
    }

    private fun installHooks() {
        loadHookHandler(
            GenerateHookHandler,
            ScopeHookHandler,
            IconHookHandler,
            BottomHookHandler,
            BgHookHandler,
            XiaomiHookHandler,
            OplusHookHandler
        )

        // 执行 Hook；toggle 绑定的成员由各自的 bindInstallToggle 自管安装状态，跳过此无条件循环
        var resolvedCount = 0
        val unresolvedNames = mutableListOf<String>()
        Members.javaClass.declaredFields.forEach { field ->
            field.makeAccessible()
            val hookManager = field.get(null)

            if (hookManager is HookManager && !hookManager.isToggleBound) {
                if (hookManager.member == null) unresolvedNames += field.name else resolvedCount++
                try {
                    hookManager.startHook(module)
                } catch (e: Throwable) {
                    XMLog.e(e)
                }
            }
        }
        // 非门控：汇报功能 Hook 安装情况，unresolved 多为目标方法在本 ROM 上不存在;
        // 附成员名便于从日志直接定位是哪一环断了(如 HyperOS 上 IconProvider 链路变更)
        XMLog.i {
            "[SystemUI] installHooks finished: rom=${DeviceUtils.describeRom()}, resolved=$resolvedCount" +
                    if (unresolvedNames.isEmpty()) "" else ", unresolved=${unresolvedNames.joinToString()}"
        }
    }
}
