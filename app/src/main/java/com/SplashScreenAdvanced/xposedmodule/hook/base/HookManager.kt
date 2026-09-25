package com.SplashScreenAdvanced.xposedmodule.hook.base

import com.SplashScreenAdvanced.xposedmodule.hook.utils.RemotePreferences.observe
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * 用于进行 Hook 操作的 HookManager
 *
 * @param createCondition 创建条件（如系统判断）
 * @param block 返回要 Hook 的成员
 */
class HookManager(private val createCondition: Boolean = true, block: () -> Executable?) {

    companion object {
        /**
         * 默认不执行 Hook；获取到包名后会被替换为「仅作用域内应用才执行」
         *
         * 注意 add*Hook 的默认值是 `{ defaultExecCondition() }` —— 调用时才读这个 var,
         * 因此替换它的时机只需早于任何一次 hook 触发, 与 handler 注册顺序无关
         */
        var defaultExecCondition: (() -> Boolean) = { false }

        /** 单个 hook 回调异常的最大打印次数，超出后静默（防每次启动重复全栈洪泛日志） */
        private const val HOOK_ERROR_LOG_LIMIT = 3
    }

    /** 已解析的目标成员；对外只读，供需要复用已解析反射结果的调用方使用 */
    var member: Executable? = null
        private set

    /** 已安装 hook 的句柄，供 [unhook] 使用 */
    private var hookHandle: XposedInterface.HookHandle? = null

    /** 是否由 [bindInstallToggle] 自管安装状态；true 时不参与 [Members] 的无条件安装循环 */
    var isToggleBound = false
        private set

    /** 保护 [hookHandle] 的装/卸：observe 回调可能在 Binder 线程触发，与主流程并发 */
    private val installLock = Any()

    private val beforeHooks = mutableListOf<HookParam.() -> Unit>()
    private val afterHooks = mutableListOf<HookParam.() -> Unit>()
    private var replaceHook: (HookParam.() -> Any?)? = null

    private val hasReplaceHook get() = replaceHook != null
    private val hasBeforeHooks get() = beforeHooks.isNotEmpty()
    private val hasAfterHooks get() = afterHooks.isNotEmpty()

    init {
        if (createCondition) try {
            member = block()
        } catch (e: Throwable) {
            XMLog.e(e)
        }
    }

    /** 若 member 是方法，返回其返回类型，否则 null */
    val returnType get() = (member as? Method)?.returnType

    fun addBeforeHook(execCondition: (() -> Boolean) = { defaultExecCondition() }, block: HookParam.() -> Unit): HookManager {
        beforeHooks += { if (execCondition()) block() }
        return this
    }

    fun addAfterHook(execCondition: (() -> Boolean) = { defaultExecCondition() }, block: HookParam.() -> Unit): HookManager {
        afterHooks += { if (execCondition()) block() }
        return this
    }

    /** ReplaceHook 只能存在一个，后加的覆盖先加的；条件不满足时调用原方法 */
    fun addReplaceHook(
        execCondition: (() -> Boolean) = { defaultExecCondition() },
        block: HookParam.() -> Any?
    ): HookManager {
        replaceHook = { if (execCondition()) block() else callOriginal() }
        return this
    }

    /**
     * 执行单个 before/after 回调并吞掉其异常
     *
     * libxposed 会把 [XposedInterface.Hooker.intercept] 抛出的异常原样送回宿主被 Hook 方法的调用处，
     * 落在 `build()` / `makeSplashScreenContentView()` 这类成员上足以让启动遮罩创建失败乃至 SystemUI 崩溃。
     * 单个回调失败只应让该功能失效，不应影响宿主与其它回调。
     */
    /**
     * 单个回调的异常打印配额: 同一回调失败超过 [HOOK_ERROR_LOG_LIMIT] 次后静默。
     * 否则某条 hook 在 ROM 上不匹配而每次调用都抛时, 每次应用启动都会向 logcat + 模块日志
     * 写一份完整堆栈, 洪泛 LSPosed 落盘日志。
     */
    private val hookErrorCounts = java.util.concurrent.ConcurrentHashMap<Any, Int>()

    private fun canLogHookError(key: Any): Boolean {
        val count = (hookErrorCounts[key] ?: 0) + 1
        hookErrorCounts[key] = count
        return count <= HOOK_ERROR_LOG_LIMIT
    }

    private fun invokeIsolated(hook: HookParam.() -> Unit, param: HookParam) {
        try {
            hook(param)
        } catch (e: Throwable) {
            if (canLogHookError(hook)) XMLog.e(e)
        }
    }

    fun startHook(module: XposedModule) {
        synchronized(installLock) {
            if (hookHandle != null) return  // 幂等：已安装则跳过，避免叠加 trampoline
            val m = member ?: return
            if (hasReplaceHook && (hasBeforeHooks || hasAfterHooks)) {
                XMLog.w { "Conflict: ReplaceHook 与 Before/After 不应共存，before/after 将被忽略。成员: ${m.declaringClass.name}#${m.name}" }
            }
            try {
                hookHandle = module.hook(m).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val param = HookParam(chain)

                        // ReplaceHook 优先且独占
                        if (hasReplaceHook) {
                            param.phase = HookParam.Phase.REPLACE
                            return try {
                                replaceHook!!.invoke(param)
                            } catch (e: Throwable) {
                                if (canLogHookError(replaceHook!!)) XMLog.e(e)
                                // 回调失败不能把异常抛回宿主：原方法还没跑过就补一次，
                                // 跑过了就沿用其结果，避免副作用重复执行
                                if (param.originalCalled) param.result else param.callOriginal()
                            }
                        }

                        // before：依次执行；任一回调拦截后即短路，剩余 before 不再执行
                        param.phase = HookParam.Phase.BEFORE
                        for (hook in beforeHooks) {
                            invokeIsolated(hook, param)
                            if (param.intercepted) break
                        }
                        val skipOriginal = param.intercepted

                        // 原方法（除非被 before 拦截）
                        param.phase = HookParam.Phase.AFTER
                        try {
                            if (!skipOriginal) {
                                param.result = param.proceedOriginal()
                            }
                        } finally {
                            // after：依次执行（可读写 result）。放在 finally 里是因为原方法抛异常时
                            // 也必须执行——有 after 回调负责复位状态（如 ThreadLocal 清理），
                            // 漏执行会导致该线程上的状态永久残留
                            for (hook in afterHooks) invokeIsolated(hook, param)
                        }

                        return param.result
                    }
                })
            } catch (e: Throwable) {
                XMLog.e(e)
            }
        }
    }

    /** 移除已安装的 hook（未安装时无操作）。可在 hook 回调内调用以实现"用完即弃"的自摘除 */
    fun unhook() {
        synchronized(installLock) {
            hookHandle?.unhook()
            hookHandle = null
        }
    }

    /**
     * 将本 hook 的安装状态绑定到开关条件：立即按当前值装/卸，并在 [keys] 任一变更时重新求值 [condition]。
     *
     * 绑定后本 [HookManager] 标记为 [isToggleBound]，退出 [Members] 的无条件安装循环，完全由本方法自管：
     * [condition] 为 true 时安装 trampoline，false 时卸载。适用于**仅由单一功能开关控制、
     * 且无其它 handler 依赖其 trampoline 常驻**的成员——开关关闭时（常态）连 trampoline 都不付。
     *
     * 注意：装/卸的即时性依赖 [observe] 回调按时触发（与 `ENABLE_LOG` 等既有实时项同一机制）；
     * 若某次变更事件丢失，最坏情况是该开关需等宿主进程重启才生效，而非数据错误。
     *
     * @param keys   需要监听变更的开关（通常即 [condition] 读取的那些开关）
     * @param condition 返回当前是否应安装本 hook
     */
    fun bindInstallToggle(
        module: XposedModule,
        vararg keys: PreferenceKey<Boolean>,
        condition: () -> Boolean
    ): HookManager {
        isToggleBound = true
        if (member == null) return this  // 无可 hook 成员：装/卸均 no-op，无需注册观察者
        val apply = { if (condition()) startHook(module) else unhook() }
        keys.forEach { key -> key.observe(fireImmediately = false) { apply() } }
        apply()  // 按当前开关值确定初始安装状态
        return this
    }
}
