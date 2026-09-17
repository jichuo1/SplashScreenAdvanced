package com.SplashScreenAdvanced.xposedmodule.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.manager.XposedServiceManager
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class RemotePreferenceStore(
    private val xposedManager: XposedServiceManager
) {
    private val remotePrefs: SharedPreferences?
        get() = xposedManager.currentService?.getRemotePreferences(Preferences.NAME)

    private val snapshot = ConcurrentHashMap<String, Any>()

    private val _globalReloadEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val globalReloadEvent = _globalReloadEvent.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            xposedManager.serviceFlow.collect { service ->
                snapshot.clear()
                if (service != null) {
                    _globalReloadEvent.emit(Unit)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: PreferenceKey<T>): T {
        snapshot[key.name]?.let { cached ->
            return cached as T
        }
        val prefs = remotePrefs ?: return key.default
        val fresh = readRemote(prefs, key)
        val stored: Any = if (fresh is Set<*>) HashSet(fresh) else fresh
        snapshot[key.name] = stored
        return stored as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readRemote(prefs: SharedPreferences, key: PreferenceKey<T>): T {
        return when (key.default) {
            is Boolean -> prefs.getBoolean(key.name, key.default as Boolean) as T
            is Int -> prefs.getInt(key.name, key.default as Int) as T
            is Long -> prefs.getLong(key.name, key.default as Long) as T
            is Float -> prefs.getFloat(key.name, key.default as Float) as T
            // 显式兜 null: getString 在存了 null 值时会返回 null, 直接 as T 会炸。
            // Hook 端的 RemotePreferences.getPref 本来就是这么写的, 两边保持一致
            is String -> (prefs.getString(key.name, key.default as String) ?: key.default) as T
            is Set<*> -> {
                val defSet = (key.default as? Set<String>) ?: emptySet()
                (prefs.getStringSet(key.name, defSet) ?: defSet) as T
            }
            else -> key.default
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> put(key: PreferenceKey<T>, value: T) {
        val prefs = remotePrefs ?: return
        prefs.edit {
            if (value is Set<*>) {
                putStringSet(key.name, value as Set<String>)
            } else {
                when (value) {
                    is Boolean -> putBoolean(key.name, value)
                    is Int -> putInt(key.name, value)
                    is Long -> putLong(key.name, value)
                    is Float -> putFloat(key.name, value)
                    is String -> putString(key.name, value)
                }
            }
        }
        snapshot[key.name] = if (value is Set<*>) HashSet(value as Set<*>) else value
    }

    fun setAll(map: Map<String, Any>) {
        snapshot.clear()
        remotePrefs?.edit(true) {
            map.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> {
                        putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
            // 顺带把当前 schema 版本写进去, 放在循环之后以覆盖备份文件里带来的旧版本号:
            // 数据已经通过 checkBackupFileValid 的兼容性检查, 此刻就该按本版本的语义看待。
            // 这一项必须真的落盘, 否则 getAll() 里没有它, 导出的备份就不带版本号,
            // checkBackupFileValid 会因为 !has(versionKey) 永远返回 true, 整个校验形同虚设
            putInt(Preferences.Module.SP_VERSION.name, Preferences.VERSION)
        }
    }

    /**
     * 确保 schema 版本已落盘
     *
     * 覆盖全新安装与「重置设置」之后的场景 —— 这两种情况不会走 [setAll]
     */
    fun ensureVersionStamped() {
        val prefs = remotePrefs ?: return
        if (prefs.getInt(Preferences.Module.SP_VERSION.name, -1) == Preferences.VERSION) return
        prefs.edit { putInt(Preferences.Module.SP_VERSION.name, Preferences.VERSION) }
        snapshot[Preferences.Module.SP_VERSION.name] = Preferences.VERSION
    }

    fun getAll(): Map<String, *>? = remotePrefs?.all

    fun clearAll() {
        snapshot.clear()
        remotePrefs?.edit(true) {
            clear()
        }
    }
}
