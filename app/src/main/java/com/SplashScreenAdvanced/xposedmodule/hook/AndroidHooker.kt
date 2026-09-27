package com.SplashScreenAdvanced.xposedmodule.hook

import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.base.HookManager
import com.SplashScreenAdvanced.xposedmodule.hook.utils.DexHostQueries
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.getField
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HostDexLookup
import com.SplashScreenAdvanced.xposedmodule.hook.utils.toTyped
import com.SplashScreenAdvanced.xposedmodule.hook.utils.RemotePreferences.get
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import com.highcapable.kavaref.KavaRef.Companion.resolve
import io.github.libxposed.api.XposedModule
import java.io.File

/**
 * Android 系统相关 Hook
 */
object AndroidHooker {
    /** 保存宿主（system_server）classLoader，供热重载后重新安装 Hook 使用 */
    @Volatile
    var classLoader: ClassLoader? = null
        private set

    fun init(module: XposedModule, classLoader: ClassLoader) {
        this.classLoader = classLoader
        val servicesJar = sequenceOf(
            "/system/framework/services.jar",
            "/system_ext/framework/services.jar",
        ).firstOrNull { File(it).isFile }
        HostDexLookup.attach(classLoader, cacheDir = null, apkPath = servicesJar)
        try {
            installHooks(module)
        } finally {
            HostDexLookup.closeBridge()
        }
    }

    private fun installHooks(module: XposedModule) {
        val activityRecordClass = HostDexLookup.findClass(
            "com.android.server.wm.ActivityRecord",
            query = DexHostQueries.activityRecord,
        )
        if (activityRecordClass == null) {
            XMLog.e { "[Android] ActivityRecord not found, skip system_server hooks" }
            return
        }

        // launchedFromSystemSurface 进程内恒定, 解析一次复用; 下方 hook 每次 activity 启动都会执行,
        // 不能在 hook 体内做全表反射扫描
        val launchedFromSystemSurface = activityRecordClass.resolve().optional().firstMethodOrNull {
            name = "launchedFromSystemSurface"
            parameterCount = 0
        }?.toTyped<Boolean>()

        // 统计成员解析结果, 末尾统一汇报——ROM 改动签名(如参数个数变化)时静默不装,
        // 无日志则无法区分「Hook 没装」与「装了但偏好为 false」
        var resolvedCount = 0
        val unresolvedNames = mutableListOf<String>()
        fun HookManager.counted(name: String): HookManager = also {
            if (it.member == null) unresolvedNames += name else resolvedCount++
        }

        /**
         * 强制显示遮罩
         *
         * 类原始位置在 services.jar 中
         *
         * 此处在 evaluateStartingWindowTheme() 中被调用，最终将参数传递给 showStartingWindow()
         */
        HookManager {
            activityRecordClass.resolve().optional().firstMethodOrNull {
                name = "validateStartingWindowTheme"
                parameterCount = 3
            }?.self
        }.counted("validateStartingWindowTheme").addBeforeHook({ true }) {
            val pkgName = args(1).string()
            // 惰性求值: 功能未启用 / 不在列表时, 不触发 launchedFromSystemSurface 反射调用
            val isForceShowSS = Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN.get()
                    || (Preferences.Display.FORCE_SHOW_SPLASH_SCREEN.get()
                    && pkgName in Preferences.AppList.FORCE_SHOW_SPLASH_SCREEN_LIST.get()
                    && (!Preferences.Display.REDUCE_SPLASH_SCREEN.get()
                    || launchedFromSystemSurface?.invoke(instance) == true))

            if (isForceShowSS) resultTrue()
            printLog { "[Android] validateStartingWindowTheme():${if (isForceShowSS) "" else " not"} force show $pkgName splash screen" }
        }.startHook(module)

        // 热启动时生成启动遮罩
        // AOSP 签名固定 7 参 (末参 TaskSnapshot); OneUI 8.5 实测该方法已不存在 (改名/内联进
        // addStartingWindow) → 解析失败。兜底放宽为「同名 + 前两参 boolean (newTask, taskSwitch)」,
        // 保证 args[1] 语义不漂移; 彻底缺失时由下方 showStartingWindow 的参数改写兜底,
        // 末尾还会 dump ActivityRecord 上候选方法供定位
        val getStartingWindowTypeHook = HookManager {
            activityRecordClass.resolve().optional().let { resolver ->
                resolver.firstMethodOrNull {
                    name = "getStartingWindowType"
                    parameterCount = 7
                } ?: resolver.firstMethodOrNull {
                    name = "getStartingWindowType"
                    parameters { types ->
                        types.size >= 2 &&
                                types[0] == Boolean::class.javaPrimitiveType &&
                                types[1] == Boolean::class.javaPrimitiveType
                    }
                }
            }?.self
        }.counted("getStartingWindowType")

        // 彻底关闭 Splash Screen
        HookManager {
            activityRecordClass.resolve().optional().firstMethodOrNull {
                name = "showStartingWindow"
                parameterCount = 7
            }?.self
        }.counted("showStartingWindow").addBeforeHook({ true }) {
            val currentPkgName = instance!!.getField<String>("packageName")

            val isDisableSS = Preferences.Display.DISABLE_SPLASH_SCREEN.get()
            printLog { "[Android] showStartingWindow():${if (isDisableSS) "" else " not"} disable $currentPkgName splash screen" }
            if (isDisableSS) {
                resultNull()
                return@addBeforeHook
            }

            // OneUI 等 ROM 把类型决策内联进 addStartingWindow/showStartingWindow, getStartingWindowType
            // 整方法不存在 → 结果改写无从谈起。退而求其次: 把 processRunning(arg3, AOSP 7 参签名已确认
            // 参数序与 AOSP 一致) 置 false —— 内联判定式 (newTask || !processRunning ||
            // (taskSwitch && !activityCreated)) 必然命中 → 返回 SPLASH 类型。
            // 判定口径与 validateStartingWindowTheme 一致: 全局强制 或 列表内强制显示
            val isForceShow = Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN.get()
                    || (Preferences.Display.FORCE_SHOW_SPLASH_SCREEN.get()
                    && currentPkgName in Preferences.AppList.FORCE_SHOW_SPLASH_SCREEN_LIST.get()
                    && (!Preferences.Display.REDUCE_SPLASH_SCREEN.get()
                    || launchedFromSystemSurface?.invoke(instance) == true))
            if (getStartingWindowTypeHook.member == null && isForceShow) {
                args(3).set(false)
                printLog { "[Android] showStartingWindow(): force processRunning=false for $currentPkgName (inlined type decision)" }
            }
        }.startHook(module)

        getStartingWindowTypeHook.addBeforeHook({ true }) {
            // 放宽签名后不假定参数布局: 安全读取 taskSwitch, 非 Boolean 视为 false (no-op)
            val taskSwitch = args.getOrNull(1) as? Boolean == true
            val isHotStartCompatible = Preferences.Display.ENABLE_HOT_START_COMPATIBLE.get()
                    && Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN.get()
                    && taskSwitch
            if (isHotStartCompatible) result = 2
            printLog { "[Android] getStartingWindowType():${if (isHotStartCompatible) "" else " not"} set result to 2" }
        }.addAfterHook({ true }) {
            // FORCE_ENABLE 全局强制: 原方法判定 NONE(0) 时改写为 SPLASH(2), 覆盖非热启动路径
            if (Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN.get() && (result as? Int) == 0) {
                result = 2
                printLog { "[Android] getStartingWindowType(): force NONE -> SPLASH_SCREEN(2)" }
            }
        }.startHook(module)

        // 非门控: 汇报 system_server 侧 Hook 安装情况 (与 SystemUI 侧 installHooks 汇报对应)
        XMLog.i {
            "[Android] installHooks finished: resolved=$resolvedCount" +
                    if (unresolvedNames.isEmpty()) "" else ", unresolved=${unresolvedNames.joinToString()}"
        }

        // getStartingWindowType 仍解析失败(改名/内联)时, dump ActivityRecord 上疑似决策方法,
        // 下一次反馈日志可直接给出真实签名
        if ("getStartingWindowType" in unresolvedNames) {
            val candidates = activityRecordClass.declaredMethods
                .filter { it.name.contains("startingWindow", ignoreCase = true) }
                .joinToString { m ->
                    "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"
                }
            XMLog.w { "[Android] getStartingWindowType unresolved; ActivityRecord candidates: $candidates" }
        }
    }
}
