package com.SplashScreenAdvanced.xposedmodule.manager

import com.SplashScreenAdvanced.xposedmodule.data.Scope
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class XposedServiceManager : XposedServiceHelper.OnServiceListener {
    private val _serviceFlow = MutableStateFlow<XposedService?>(null)
    val serviceFlow = _serviceFlow.asStateFlow()

    val currentService: XposedService?
        get() = _serviceFlow.value

    init {
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        XMLog.d {
            "XposedServiceManager.onServiceBind"
        }
        _serviceFlow.value = service
    }

    override fun onServiceDied(service: XposedService) {
        XMLog.d {
            "XposedServiceManager.onServiceDied"
        }
        _serviceFlow.value = null
    }

    /**
     * 被 Hook 进程是否需要重启的判定结果。
     *
     * @property systemUI SystemUI 是否需要重启
     * @property android system_server 是否需要重启，null 表示未获取到（未注入 / 不在作用域）
     */
    data class RestartState(val systemUI: Boolean, val android: Boolean?)

    /**
     * 通过 libxposed service API 102 的 [XposedService.getRunningTargets] 判定各被 Hook 进程
     * 是否仍在运行旧模块代码（[HookedTarget.State.STALE]）。
     *
     * 框架低于 API 102 或查询失败时返回 null（调用方应据此保持安全默认值，避免误报）。
     */
    fun queryRestartState(): RestartState? {
        val service = currentService ?: return null
        if (service.apiVersion < XposedService.API_102) return null
        val targets = runCatching { service.runningTargets }.getOrNull() ?: return null
        fun List<HookedTarget>.staleOrFailed(processName: String): Boolean? =
            firstOrNull { it.processName == processName }
                ?.let { it.state == HookedTarget.State.STALE || it.state == HookedTarget.State.FAILED }
        return RestartState(
            systemUI = targets.staleOrFailed(Scope.SYSTEM_UI) ?: false,
            android = targets.staleOrFailed(SYSTEM_SERVER_PROCESS)
        )
    }

    companion object {
        /** system_server 的进程名（注意区别于 libxposed 作用域关键字 [Scope.SYSTEM]） */
        private const val SYSTEM_SERVER_PROCESS = "system_server"
    }
}
