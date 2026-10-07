package com.SplashScreenAdvanced.xposedmodule.state

import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.manager.XposedServiceManager
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModuleUiState(
    val moduleActive: Boolean = false,
    val devMode: Boolean = false,
    val systemUIRestartNeeded: Boolean = true,
    val androidRestartNeeded: Boolean? = null,
    val xposedFrameworkName: String = "Xposed",
    val xposedApiVersion: Int = 0,
)

/** Application-owned status; Activity collectors stop when their native page is hidden. */
class GlobalUIViewModel(
    private val repo: GlobalPreferencesRepository,
    private val xposedServiceManager: XposedServiceManager,
) {
    val configFlow = repo.uiConfigFlow
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ModuleUiState())
    val state = mutableState.asStateFlow()
    val moduleActive: Boolean get() = state.value.moduleActive
    val devMode: Boolean get() = state.value.devMode
    val systemUIRestartNeeded: Boolean get() = state.value.systemUIRestartNeeded
    val androidRestartNeeded: Boolean? get() = state.value.androidRestartNeeded
    val xposedFrameworkName: String get() = state.value.xposedFrameworkName
    val xposedApiVersion: Int get() = state.value.xposedApiVersion
    private var restartGeneration = 0L

    init {
        scope.launch {
            xposedServiceManager.serviceFlow.collect { service ->
                mutableState.update {
                    it.copy(moduleActive = isModuleActivated(service),
                        xposedFrameworkName = service?.frameworkName ?: "Xposed",
                        xposedApiVersion = service?.apiVersion ?: 0,
                        devMode = if (service != null) repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS) else it.devMode)
                }
                refreshRestartState()
            }
        }
        scope.launch {
            repo.preferenceUpdates
                .filter { it == Preferences.Dev.ENABLE_DEV_SETTINGS }
                .collect { syncDevMode() }
        }
        scope.launch {
            repo.globalReloadEvent.collect {
                syncDevMode()
            }
        }
    }

    /**
     * 刷新被 Hook 进程是否需要重启的状态
     *
     * [XposedServiceManager.queryRestartState] 内部是对 Xposed 框架服务的同步 binder 调用,
     * 而本方法在 Activity.onResume 里也会被调到, 框架侧慢一点就会直接卡住主线程, 所以放到 IO 线程执行,
     * 只把结果切回主线程写状态
     */
    fun refreshRestartState() {
        val generation = ++restartGeneration
        scope.launch {
            val state = withContext(Dispatchers.IO) { xposedServiceManager.queryRestartState() }
            if (generation == restartGeneration) mutableState.update {
                it.copy(systemUIRestartNeeded = state?.systemUI ?: false, androidRestartNeeded = state?.android)
            }
        }
    }

    fun syncDevMode() {
        mutableState.update { it.copy(devMode = repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS)) }
    }

    private fun isModuleActivated(service: XposedService?): Boolean {
        if (service == null) return false
        val cap = service.frameworkProperties
        return (cap and XposedService.PROP_CAP_SYSTEM != 0L) &&
            (cap and XposedService.PROP_CAP_REMOTE != 0L)
    }
}
