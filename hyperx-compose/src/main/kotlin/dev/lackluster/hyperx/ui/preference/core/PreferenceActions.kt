package dev.lackluster.hyperx.ui.preference.core

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface PreferenceActions {
    fun <T : Any> get(key: PreferenceKey<T>): T
    fun <T : Any> update(key: PreferenceKey<T>, value: T)
    val preferenceUpdates: Flow<PreferenceKey<*>>
        get() = emptyFlow()

    /** 远程配置整体重载 (服务绑定 / 导入 / 重置) 时发出, 订阅方应重新 get */
    val preferenceReloads: Flow<Unit>
        get() = emptyFlow()
}

val LocalPreferenceActions = compositionLocalOf<PreferenceActions> {
    error("PreferenceActions not provided")
}