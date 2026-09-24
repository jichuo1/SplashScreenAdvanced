package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.SplashScreenAdvanced.xposedmodule.data.StartingWindowInfo
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.delayScope
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.getMapPrefs
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.hook.utils.ReflectCache
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import io.github.libxposed.api.XposedInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import kotlin.time.Duration.Companion.milliseconds

/**
 * 此对象用于处理 基础设置 和 实验功能 中的 Hook
 */
object GenerateHookHandler : BaseHookHandler() {
    // 以下状态由 makeSplashScreenContentView (shell 的启动遮罩线程) 写入,
    // 由 removeStartingWindow / build() 等可能位于其它线程的 hook 读取,
    // 必须 @Volatile, 否则跨线程可见性没有保证
    @Volatile
    var currentPackageName = ""

    @Volatile
    var currentComponentName = ""

    @Volatile
    var currentActivity = ""

    @Volatile
    var currentApplicationInfo = null as ApplicationInfo?

    @Volatile
    var currentActivityInfo = null as ActivityInfo?

    @Volatile
    var exceptCurrentApp = false

    /** 本次启动遮罩流程的开始时刻 (uptime, ms); 0 表示当前不在流程中 */
    @Volatile
    private var hookingStartedAt = 0L

    /**
     * 是否正处于一次启动遮罩构建流程中
     *
     * 带超时兜底: 正常由 `removeStartingWindow` 复位, 但宿主若因异常中断或走了别的移除路径而没能调到那里,
     * 原先的纯布尔标志会永久停在 true, 使所有依赖 `defaultExecCondition` 的 hook 在整个 SystemUI
     * 生命周期内常开。[HOOKING_TIMEOUT_MS] 取得足够宽松 (远超任何正常冷启动 + 最小持续时长),
     * 只用于兜住这种已经异常的状态, 不会影响正常流程
     */
    var isHooking: Boolean
        get() = hookingStartedAt != 0L &&
                SystemClock.uptimeMillis() - hookingStartedAt < HOOKING_TIMEOUT_MS
        set(value) {
            hookingStartedAt = if (value) SystemClock.uptimeMillis() else 0L
        }

    private const val HOOKING_TIMEOUT_MS = 60_000L

    /** 首次触发 `makeSplashScreenContentView` 时落一条非门控日志，用于区分「Hook 未安装」与「安装了但宿主从未调用」 */
    private val firstContentViewLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 延迟调用 removeStartingWindow 原方法 */
    private val delayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 重置当前应用信息的缓存
     */
    private fun resetCache() {
        currentPackageName = ""
        currentComponentName = ""
        currentActivity = ""
        currentApplicationInfo = null
        currentActivityInfo = null
        isHooking = false
        exceptCurrentApp = false

        IconHookHandler.resetCache()
        BgHookHandler.resetCache()
    }

    /** 开始 Hook */
    override fun onHook() {

        // Hook 起始位置, 获取应用信息
        SystemUIHooker.Members.makeSplashScreenContentView.addBeforeHook({ true }) {
            if (firstContentViewLogged.compareAndSet(false, true)) {
                XMLog.i { "****** makeSplashScreenContentView(): first invocation" }
            }
            // args[1] 已知形态: 直接 ActivityInfo / 包装类的 targetActivityInfo 字段 /
            // 包装类 taskInfo 字段里的 topActivityInfo。字段被 ROM 改名时用类型扫描兜底;
            // 全失败也不能 NPE —— 否则 currentPackageName 永远空着, 整条图标链静默失效
            var activityInfo: ActivityInfo? = args.getOrNull(1) as? ActivityInfo
            if (activityInfo == null) {
                val arg = args.getOrNull(1)
                if (arg != null) {
                    activityInfo = ReflectCache.getField<ActivityInfo>(arg, "targetActivityInfo")
                        ?: ReflectCache.getField<Any>(arg, "taskInfo")
                            ?.let { ReflectCache.getField<ActivityInfo>(it, "topActivityInfo") }
                        ?: extractActivityInfo(arg)
                }
                // 最后兜底: 实参表其它位置的 ActivityInfo
                if (activityInfo == null) {
                    activityInfo = args.firstNotNullOfOrNull { it as? ActivityInfo }
                }
            }
            if (activityInfo == null) {
                XMLog.w { "makeSplashScreenContentView(): ActivityInfo extraction failed, args=${args.contentToString()}" }
                return@addBeforeHook
            }

            isHooking = true
            currentPackageName = activityInfo.packageName
            currentComponentName = activityInfo.name
            currentActivity = activityInfo.targetActivity ?: "unknown activity"
            currentApplicationInfo = activityInfo.applicationInfo
            currentActivityInfo = activityInfo
            exceptCurrentApp = isExcept()

            printLog {
                "****** $currentPackageName; $currentActivity: makeSplashScreenContentView(): ${if (exceptCurrentApp) "except" else "allow"} this app"
            }

            /**
             * 强制开启启动遮罩
             *
             * 直接干预 build() 中的 if 判断
             */
            val forceEnableSplashScreen = prefs.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)
            if (forceEnableSplashScreen) {
                if (!exceptCurrentApp) {
                    args(args.indexOfFirst { it is Int }).set(StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN)
                    printLog { "makeSplashScreenContentView(): forceEnableSplashScreen, set mSuggestType to STARTING_WINDOW_TYPE_SPLASH_SCREEN(1)" }
                }
            }
        }

        // ---------- ROM 兼容: 强制样式落到最终决策点 ----------
        // makeSplashScreenContentView 的 suggestType 只是入参, 会被 backport 了新版 splash 代码的
        // 三方 ROM (如 AfterlifeOS) 按 canUseIcon() 重映射成 SOLID_COLOR; 而 chooseStyle() 写入的
        // mSuggestType 才是 SplashViewBuilder.build() 唯一的判断依据, 强制必须落在这一层。
        // 本 Hook 无条件安装, 但只在开关开启时才改写参数, 其余 ROM 行为保持不变。
        SystemUIHooker.Members.chooseStyle_SplashViewBuilder.addBeforeHook({ true }) {
            if (!prefs.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)) return@addBeforeHook
            if (exceptCurrentApp || currentPackageName.isEmpty()) return@addBeforeHook

            args(0).set(StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN)
            printLog { "chooseStyle(): force STARTING_WINDOW_TYPE_SPLASH_SCREEN for $currentPackageName" }
        }

        // 兜底: canUseIcon() 为 false 时 ROM 会把 SPLASH_SCREEN 降级成 SOLID_COLOR(纯色且不绘制图标)
        SystemUIHooker.Members.canUseIcon_SplashscreenContentDrawer.addBeforeHook({ true }) {
            if (!prefs.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)) return@addBeforeHook
            if (exceptCurrentApp || currentPackageName.isEmpty()) return@addBeforeHook

            resultTrue()
        }

        // 遮罩最小持续时间, 也是 Hook 结束位置, 清除缓存的应用信息
        SystemUIHooker.Members.removeStartingWindow.addReplaceHook({ true }) {
            if (exceptCurrentApp || !isHooking) callOriginal()
            else when (currentPackageName) {
                "" -> {
                    callOriginal()
                }

                // 单独配置应用最小持续时长
                in prefs.get(Preferences.AppList.MIN_DURATION_LIST) -> {
                    val configMap = getMapPrefs(Preferences.AppList.MIN_DURATION_CONFIG_MAP)
                    try {
                        val duration = configMap[currentPackageName].toString().toLong()

                        if (duration == 0L) callOriginal()
                        else {
                            printLog { "removeStartingWindow(): remove splash screen of $currentPackageName after $duration ms" }
                            delayCallOriginal(duration, instance, args)
                        }

                    } catch (_: NumberFormatException) {
                        printLog { "removeStartingWindow(): $currentPackageName: a NumberFormatException is threw, maybe it's MIN_DURATION config is incorrect" }
                        callOriginal()
                    }
                }

                // 默认值
                else -> prefs.get(Preferences.Display.MIN_DURATION).let { duration ->
                    if (duration == 0) callOriginal()
                    else {
                        printLog { "removeStartingWindow(): remove splash screen of $currentPackageName after $duration ms (default value)" }
                        delayCallOriginal(duration.toLong(), instance, args)
                    }
                }
            }

            // 清除缓存的应用信息
            resetCache()
            null
        }
    }

    /**
     * 在包装对象中按类型扫描第一个 [ActivityInfo] 字段 (含父类)
     *
     * 供 ROM 改写字段名后的兜底解析: `targetActivityInfo`/`mTargetActivityInfo`/`activityInfo`
     * 等命名都能命中, 与具体字段名解耦。
     */
    private fun extractActivityInfo(arg: Any): ActivityInfo? {
        var cls: Class<*>? = arg.javaClass
        while (cls != null) {
            for (field in cls.declaredFields) {
                if (ActivityInfo::class.java.isAssignableFrom(field.type)) {
                    return runCatching {
                        field.isAccessible = true
                        field.get(arg) as? ActivityInfo
                    }.getOrNull()
                }
            }
            cls = cls.superclass
        }
        return null
    }

    /**
     * 延迟 [duration] 毫秒后调用 `removeStartingWindow` 的原方法
     *
     */
    private fun delayCallOriginal(duration: Long, instance: Any?, args: Array<Any?>) {
        // 复用 Members.removeStartingWindow 已解析的成员，避免重复反射解析，
        // 同时保证延迟调用与 hook 落点是同一个方法
        val method = SystemUIHooker.Members.removeStartingWindow.member as? Method
        val argsCopy = args.copyOf()
        delayScope.launch {
            delay(duration.milliseconds)
            try {
                if (method != null) {
                    val invoker: XposedInterface.Invoker<*, Method> = module.getInvoker(method)
                    invoker.setType(XposedInterface.Invoker.Type.Origin())
                    invoker.invoke(instance, *argsCopy)
                } else {
                    XMLog.w { "delayCallOriginal(): removeStartingWindow Method 解析失败，无法延迟调用原方法" }
                }
            } catch (e: Throwable) {
                XMLog.e(e)
            }
        }
    }

    /**
     * 取消所有挂起的延迟摘除协程
     *
     * 热重载时由**旧代**调用：冻结旧代前主动取消，避免残留协程在新一代生效后误触发。
     */
    fun cancelPendingDelays() {
        delayScope.coroutineContext.cancelChildren()
    }

    /**
     * 判断是否应执行Hook操作
     *
     * @return 是否应执行Hook操作
     */
    private fun isExcept(): Boolean {
        return if (currentPackageName.isBlank())
            true
        else {
            val list = prefs.get(Preferences.AppList.CUSTOM_SCOPE_LIST)
            val isExceptionMode = prefs.get(Preferences.Scope.IS_CUSTOM_SCOPE_EXCEPTION_MODE)
            (prefs.get(Preferences.Scope.ENABLE_CUSTOM_SCOPE)
                    && ((isExceptionMode && (currentPackageName in list))
                    || (!isExceptionMode && currentPackageName !in list)))
        }
    }
}
