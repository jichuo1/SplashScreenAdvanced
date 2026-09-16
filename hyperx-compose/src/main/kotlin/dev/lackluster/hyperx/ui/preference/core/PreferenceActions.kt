package dev.lackluster.hyperx.ui.preference.core

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface PreferenceActions {
    fun <T : Any> get(key: PreferenceKey<T>): T
    fun <T : Any> update(key: PreferenceKey<T>, value: T)
    val preferenceUpdates: Flow<PreferenceKey<*>>
        get() = emptyFlow()
}

val LocalPreferenceActions = compositionLocalOf<PreferenceActions> {
    error("PreferenceActions not provided")
}