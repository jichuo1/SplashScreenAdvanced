package com.SplashScreenAdvanced.xposedmodule.hook.utils

import android.content.SharedPreferences
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object RemotePreferences {
    private lateinit var remotePrefs: SharedPreferences

    private val observerRoutingTable = ConcurrentHashMap<String, CopyOnWriteArraySet<() -> Unit>>()
    private val snapshot = ConcurrentHashMap<String, Any>()
    private val globalListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == null) {
            snapshot.clear()
            observerRoutingTable.values.forEach { observers ->
                observers.forEach { action -> action.invoke() }
            }
            return@OnSharedPreferenceChangeListener
        }
        snapshot.remove(changedKey)
        observerRoutingTable[changedKey]?.forEach { action ->
            action.invoke()
        }
    }
    private var isGlobalListenerRegistered = AtomicBoolean(false)

    fun init(module: XposedModule) {
        remotePrefs = module.getRemotePreferences(Preferences.NAME)
        snapshot.clear()
        if (isGlobalListenerRegistered.compareAndSet(false, true)) {
            remotePrefs.registerOnSharedPreferenceChangeListener(globalListener)
        }
    }

    private val isInitialized: Boolean
        get() = this::remotePrefs.isInitialized

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readRemote(key: PreferenceKey<T>): T {
        if (!isInitialized) return key.default

        return when (key.default) {
            is Boolean -> remotePrefs.getBoolean(key.name, key.default as Boolean) as T
            is Int -> remotePrefs.getInt(key.name, key.default as Int) as T
            is Long -> remotePrefs.getLong(key.name, key.default as Long) as T
            is Float -> remotePrefs.getFloat(key.name, key.default as Float) as T
            is String -> (remotePrefs.getString(key.name, key.default as String) ?: key.default) as T
            is Set<*> -> {
                val defSet = (key.default as? Set<String>) ?: emptySet()
                (remotePrefs.getStringSet(key.name, defSet) ?: defSet) as T
            }
            else -> key.default
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getPref(key: PreferenceKey<T>): T {
        snapshot[key.name]?.let { cached ->
            return cached as T
        }
        val fresh = readRemote(key)
        val stored: Any = if (fresh is Set<*>) HashSet(fresh) else fresh
        snapshot[key.name] = stored
        return stored as T
    }

    fun <T : Any> PreferenceKey<T>.get(): T = getPref(this)

    fun <T : Any> PreferenceKey<T>.lazyGet(): Lazy<T> = lazy {
        getPref(this)
    }

    fun <T : Any> PreferenceKey<T>.observe(
        fireImmediately: Boolean = true,
        action: (T) -> Unit
    ): () -> Unit {
        if (!isInitialized) return {}

        if (isGlobalListenerRegistered.compareAndSet(false, true)) {
            remotePrefs.registerOnSharedPreferenceChangeListener(globalListener)
        }

        val wrappedAction: () -> Unit = { action(this.get()) }
        val observersForThisKey = observerRoutingTable.getOrPut(this.name) { CopyOnWriteArraySet() }
        observersForThisKey.add(wrappedAction)

        if (fireImmediately) {
            action(this.get())
        }

        return {
            observersForThisKey.remove(wrappedAction)
            if (observersForThisKey.isEmpty()) {
                observerRoutingTable.remove(this.name)
            }
        }
    }
}
