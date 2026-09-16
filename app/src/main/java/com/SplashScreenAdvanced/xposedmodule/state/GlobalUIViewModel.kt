package com.SplashScreenAdvanced.xposedmodule.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.manager.XposedServiceManager
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalUIViewModel(
    private val repo: GlobalPreferencesRepository,
    private val xposedServiceManager: XposedServiceManager,
) : ViewModel() {
    val configFlow = repo.uiConfigFlow

    var moduleActive by mutableStateOf(false)
        private set
    var devMode by mutableStateOf(false)
        private set
    var systemUIRestartNeeded by mutableStateOf(true)
        private set
    var androidRestartNeeded by mutableStateOf<Boolean?>(null)
        private set
    var xposedFrameworkName by mutableStateOf("Xposed")
        private set
    var xposedApiVersion by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            xposedServiceManager.serviceFlow.collect { service ->
                moduleActive = isModuleActivated(service)
                xposedFrameworkName = service?.frameworkName ?: "Xposed"
                xposedApiVersion = service?.apiVersion ?: 0
                if (service != null) {
                    devMode = repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS)
                }
                refreshRestartState()
            }
        }
        viewModelScope.launch {
            repo.preferenceUpdates
                .filter { it == Preferences.Dev.ENABLE_DEV_SETTINGS }
                .collect { devMode = repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS) }
        }
        viewModelScope.launch {
            repo.globalReloadEvent.collect {
                devMode = repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS)
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
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { xposedServiceManager.queryRestartState() }
            systemUIRestartNeeded = state?.systemUI ?: false
            androidRestartNeeded = state?.android
        }
    }

    fun syncDevMode() {
        devMode = repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS)
    }

    private fun isModuleActivated(service: XposedService?): Boolean {
        if (service == null) return false
        val cap = service.frameworkProperties
        return service.apiVersion >= XposedService.API_102 &&
            (cap and XposedService.PROP_CAP_SYSTEM != 0L) &&
            (cap and XposedService.PROP_CAP_REMOTE != 0L)
    }
}
