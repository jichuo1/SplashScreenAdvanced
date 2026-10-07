package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.SplashScreenAdvanced.xposedmodule.data.StartingWindowInfo
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.utils.DeferredRemovalQueue
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookCallScope
import com.SplashScreenAdvanced.xposedmodule.hook.utils.StartingTaskRegistry
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.getMapPrefs
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.hook.utils.ReflectCache
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * 此对象用于处理 基础设置 和 实验功能 中的 Hook
 */
object GenerateHookHandler : BaseHookHandler() {
    internal class RenderSession(val entered: Boolean) {
        var activityInfo: ActivityInfo? = null
        var except: Boolean = true
        val icon = IconHookHandler.RenderState()
        var tmpAttrs: Any? = null
        var backgroundColorOverride: Int? = null
    }

    private data class StartingTask(val packageName: String, val durationMs: Long)
    private val calls = HookCallScope<RenderSession>()
    private val tasks = StartingTaskRegistry<StartingTask>(SystemClock::uptimeMillis)
    private val removals = DeferredRemovalQueue()
    internal val currentSession get() = calls.current
    val currentActivityInfo: ActivityInfo? get() = currentSession?.activityInfo
    val currentApplicationInfo: ApplicationInfo? get() = currentActivityInfo?.applicationInfo
    val currentPackageName: String get() = currentActivityInfo?.packageName.orEmpty()
    val currentComponentName: String get() = currentActivityInfo?.name.orEmpty()
    val currentActivity: String get() = currentActivityInfo?.targetActivity ?: "unknown activity"
    val exceptCurrentApp: Boolean get() = currentSession?.except != false
    val isHooking: Boolean get() = currentSession?.activityInfo != null

    /** 首次触发 `makeSplashScreenContentView` 时落一条非门控日志，用于区分「Hook 未安装」与「安装了但宿主从未调用」 */
    private val firstContentViewLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** suggestType 实参缺失告警只打一次, 避免每次启动重复输出签名 dump */
    @Volatile
    private var warnedNoSuggestArg = false

    /** chooseStyle 缺失时 build() 兜底写的字段名; null=未扫描, ""=扫描无果(负缓存) */
    @Volatile
    private var suggestTypeFieldName: String? = null

    /** suggest-type 字段也扫描失败时告警只打一次 */
    @Volatile
    private var warnedNoSuggestField = false

    /** 在 builder 类(含父类)上找 suggestType 的 int 字段; 找不到返回 null */
    private fun resolveSuggestTypeField(clazz: Class<*>): String? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredFields.firstOrNull {
                it.type == Int::class.javaPrimitiveType && it.name.contains("suggest", ignoreCase = true)
            }?.let { return it.name }
            current = current.superclass
        }
        return null
    }

    /** 开始 Hook */
    override fun onHook() {

        // Hook 起始位置, 获取应用信息
        SystemUIHooker.Members.makeSplashScreenContentView.addBeforeHook({ true }) {
            // 即使提取失败也压入空会话，防止嵌套调用误用外层应用的数据。
            val session = RenderSession(removals.enterCall())
            calls.enter(session)
            if (!session.entered) return@addBeforeHook
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

            session.activityInfo = activityInfo
            session.except = isExcept(activityInfo.packageName)
            tasks.put(taskId(args, creation = true), StartingTask(
                activityInfo.packageName,
                if (session.except) 0L else minimumDuration(activityInfo.packageName),
            ))

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
                    // indexOfFirst 可能返回 -1 (签名被 ROM 改为无 int 参), args(-1) 会抛数组越界
                    val intArgIndex = args.indexOfFirst { it is Int }
                    if (intArgIndex >= 0) {
                        args(intArgIndex).set(StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN)
                        printLog { "makeSplashScreenContentView(): forceEnableSplashScreen, set mSuggestType to STARTING_WINDOW_TYPE_SPLASH_SCREEN(1)" }
                    } else if (!warnedNoSuggestArg) {
                        warnedNoSuggestArg = true
                        XMLog.w {
                            "makeSplashScreenContentView(): forceEnable but no Int arg; args=" +
                                    args.joinToString { it?.javaClass?.simpleName ?: "null" }
                        }
                    }
                }
            }
        }

        // HookManager 的 finally 路径也执行 after：异常/提前返回均恢复外层会话。
        SystemUIHooker.Members.makeSplashScreenContentView.addAfterHook({ true }) {
            val session = calls.current ?: return@addAfterHook
            try {
                session.icon.steeredTmpAttrs?.let { ReflectCache.setField(it, "mIconBgColor", 0) }
            } finally {
                calls.exit()
                if (session.entered) removals.exitCall()
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
            // 签名放宽后不再假定 arg0 为 int —— ROM 改成非 int 首参时静默跳过,
            // 避免把 Int 写进非 int 参数导致宿主方法内崩溃
            if (args.getOrNull(0) !is Int) return@addBeforeHook

            args(0).set(StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN)
            printLog { "chooseStyle(): force STARTING_WINDOW_TYPE_SPLASH_SCREEN for $currentPackageName" }
        }

        // 兜底: canUseIcon() 为 false 时 ROM 会把 SPLASH_SCREEN 降级成 SOLID_COLOR(纯色且不绘制图标)
        SystemUIHooker.Members.canUseIcon_SplashscreenContentDrawer.addBeforeHook({ true }) {
            if (!prefs.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)) return@addBeforeHook
            if (exceptCurrentApp || currentPackageName.isEmpty()) return@addBeforeHook

            resultTrue()
        }

        // ROM 移除/改名 chooseStyle (实测 OneUI 8.5 未解析) 时的最终兜底:
        // 在 builder build() 前直接向实例写 suggestType 字段。
        // chooseStyle 已解析时交给它处理, 此处直接跳过避免双写
        SystemUIHooker.Members.build_StartingWindowViewBuilder.addBeforeHook({ true }) {
            if (!prefs.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)) return@addBeforeHook
            if (exceptCurrentApp || currentPackageName.isEmpty()) return@addBeforeHook
            // chooseStyle 已解析且首参为 int 时交给它处理; 解析成功但签名被改
            // (首参非 int) 时其 hook 体不会执行, 同样需要此处字段兜底
            val chooseStyleMember = SystemUIHooker.Members.chooseStyle_SplashViewBuilder.member
            if ((chooseStyleMember as? Method)?.parameterTypes?.firstOrNull() ==
                Int::class.javaPrimitiveType
            ) return@addBeforeHook

            val builder = instance ?: return@addBeforeHook
            val fieldName = suggestTypeFieldName ?: resolveSuggestTypeField(builder.javaClass)
                .also { suggestTypeFieldName = it ?: "" }
            if (fieldName.isNullOrEmpty()) {
                if (!warnedNoSuggestField) {
                    warnedNoSuggestField = true
                    XMLog.w {
                        "build(): chooseStyle absent and no suggest-type int field; fields=" +
                                builder.javaClass.declaredFields.joinToString { "${it.name}:${it.type.simpleName}" }
                    }
                }
                return@addBeforeHook
            }
            ReflectCache.setField(builder, fieldName, StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN)
            printLog { "build(): force $fieldName=STARTING_WINDOW_TYPE_SPLASH_SCREEN for $currentPackageName (chooseStyle absent)" }
        }

        // 移除只消费对应任务的不可变记录，不清理其它启动会话。
        SystemUIHooker.Members.removeStartingWindow.addReplaceHook({ true }) {
            if (!removals.enterCall()) return@addReplaceHook callOriginal()
            try {
                val task = tasks.take(taskId(args, creation = false))
                if (task == null || task.durationMs <= 0L) return@addReplaceHook callOriginal()
                // 日志放在接管原方法之前；接管成功后只返回，避免异常兜底重复调用。
                printLog { "removeStartingWindow(): remove ${task.packageName} after ${task.durationMs} ms" }
                if (delayCallOriginal(task.durationMs, instance, args)) null else callOriginal()
            } finally {
                removals.exitCall()
            }
        }
    }

    private fun minimumDuration(packageName: String): Long {
        val duration = if (packageName in prefs.get(Preferences.AppList.MIN_DURATION_LIST)) {
            getMapPrefs(Preferences.AppList.MIN_DURATION_CONFIG_MAP)[packageName]?.toLongOrNull() ?: 0L
        } else prefs.get(Preferences.Display.MIN_DURATION).toLong()
        return duration.coerceAtLeast(0L)
    }

    private fun taskId(args: Array<Any?>, creation: Boolean): Int? = args.firstNotNullOfOrNull { arg ->
        if (arg == null || arg is ActivityInfo || arg is Int) null else {
            val info = if (creation) ReflectCache.getField<Any>(arg, "taskInfo") else arg
            info?.let { ReflectCache.getField<Int>(it, "taskId") }?.takeIf { it >= 0 }
        }
    }

    /**
     * 在包装对象中按类型扫描第一个 [ActivityInfo] 字段 (含父类)
     *
     * 供 ROM 改写字段名后的兜底解析: `targetActivityInfo`/`mTargetActivityInfo`/`activityInfo`
     * 等命名都能命中, 与具体字段名解耦。
     */
    /**
     * 按包装类缓存 [extractActivityInfo] 解析出的字段 (含负缓存):
     * ROM 把命名字段全部改掉时, 每次启动都会走全字段扫描 —— 类结构进程内恒定, 只扫一次
     */
    private val activityInfoFields = java.util.concurrent.ConcurrentHashMap<Class<*>, java.lang.reflect.Field>()
    private val activityInfoFieldMisses = java.util.concurrent.ConcurrentHashMap.newKeySet<Class<*>>()

    private fun extractActivityInfo(arg: Any): ActivityInfo? {
        val cls0 = arg.javaClass
        if (cls0 in activityInfoFieldMisses) return null
        val cached = activityInfoFields[cls0]
        if (cached != null) return runCatching { cached.get(arg) as? ActivityInfo }.getOrNull()

        var cls: Class<*>? = cls0
        while (cls != null) {
            for (field in cls.declaredFields) {
                if (ActivityInfo::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    activityInfoFields[cls0] = field
                    return runCatching { field.get(arg) as? ActivityInfo }.getOrNull()
                }
            }
            cls = cls.superclass
        }
        activityInfoFieldMisses += cls0
        return null
    }

    /**
     * 延迟 [duration] 毫秒后调用 `removeStartingWindow` 的原方法
     *
     */
    private fun delayCallOriginal(duration: Long, instance: Any?, args: Array<Any?>): Boolean {
        // 沿用本次宿主回调的 Looper；未知线程模型时同步放行，禁止猜测主线程。
        val looper = Looper.myLooper() ?: return false
        val method = SystemUIHooker.Members.removeStartingWindow.member as? Method ?: return false
        val invoker: XposedInterface.Invoker<*, Method> = module.getInvoker(method)
        invoker.setType(XposedInterface.Invoker.Type.Origin())
        val argsCopy = args.copyOf()
        return removals.schedule({ Handler(looper).postDelayed(it, duration) }) {
            try {
                invoker.invoke(instance, *argsCopy)
            } catch (e: Throwable) {
                XMLog.e(e)
            }
        }
    }

    /**
     * 有未完成的宿主操作时拒绝重载，待其正常执行后再重试。
     */
    fun prepareHotReload(): Boolean = removals.prepareReload()

    /**
     * 判断是否应执行Hook操作
     *
     * @param packageName 默认为当前遮罩流程中的应用; 遮罩流程之外的查询点
     *   (任务级背景色 / 预加载复用门) 需显式传入目标包名
     * @return 是否应执行Hook操作
     */
    internal fun isExcept(packageName: String = currentPackageName): Boolean {
        return if (packageName.isBlank())
            true
        else {
            val list = prefs.get(Preferences.AppList.CUSTOM_SCOPE_LIST)
            val isExceptionMode = prefs.get(Preferences.Scope.IS_CUSTOM_SCOPE_EXCEPTION_MODE)
            (prefs.get(Preferences.Scope.ENABLE_CUSTOM_SCOPE)
                    && ((isExceptionMode && (packageName in list))
                    || (!isExceptionMode && packageName !in list)))
        }
    }
}
