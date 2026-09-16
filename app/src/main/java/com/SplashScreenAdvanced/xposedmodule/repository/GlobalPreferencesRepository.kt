package com.SplashScreenAdvanced.xposedmodule.repository

import android.content.Context
import android.net.Uri
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import com.SplashScreenAdvanced.xposedmodule.utils.RemotePreferenceStore
import dev.lackluster.hyperx.ui.layout.HyperXLayoutConfig
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class GlobalPreferencesRepository(
    private val context: Context,
    private val prefStore: RemotePreferenceStore
) {
    private val _uiConfigFlow = MutableStateFlow(
        HyperXLayoutConfig(
            isBlurEnabled = Preferences.Module.MODULE_APP_BLUR.default,
            isSplitScreenEnabled = Preferences.Module.SPLIT_VIEW.default
        )
    )
    val uiConfigFlow = _uiConfigFlow.asStateFlow()

    private val _globalReloadEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val globalReloadEvent = _globalReloadEvent.asSharedFlow()

    private val _preferenceUpdates = MutableSharedFlow<PreferenceKey<*>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val preferenceUpdates = _preferenceUpdates.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        initAndCheck()

        scope.launch {
            prefStore.globalReloadEvent.collect {
                initAndCheck()
                _globalReloadEvent.emit(Unit)
            }
        }
    }

    private fun initAndCheck() {
        // 全新安装 / 重置设置之后 sp_version 不在 prefs 里, 这里补写, 保证导出的备份带版本号
        prefStore.ensureVersionStamped()

        XMLog.isDebugEnabled = prefStore.get(Preferences.Log.ENABLE_LOG)

        _uiConfigFlow.value = HyperXLayoutConfig(
            isSplitScreenEnabled = prefStore.get(Preferences.Module.SPLIT_VIEW),
            isBlurEnabled = prefStore.get(Preferences.Module.MODULE_APP_BLUR)
        )
    }

    fun <T : Any> get(key: PreferenceKey<T>): T {
        return prefStore.get(key)
    }

    fun <T : Any> update(key: PreferenceKey<T>, value: T) {
        // 1. 统一持久化写入 SP
        prefStore.put(key, value)

        _preferenceUpdates.tryEmit(key)

        // 2. 精准的局部状态更新（仅对会影响 UI 的 Key 做派发）
        when (key) {
            Preferences.Module.SPLIT_VIEW -> _uiConfigFlow.update { it.copy(isSplitScreenEnabled = value as Boolean) }
            Preferences.Module.MODULE_APP_BLUR -> _uiConfigFlow.update { it.copy(isBlurEnabled = value as Boolean) }

            Preferences.Log.ENABLE_LOG -> XMLog.isDebugEnabled = (value as Boolean)
            // 其余仅需持久化的 Key 在第 1 步已写入 SP，Hook 端可直接读取，这里无需处理。
        }
    }

    suspend fun exportBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val kvs = prefStore.getAll() ?: throw IllegalStateException("SharedPreferences not initialized")
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Can't open output stream")

            val jsonObject = JSONObject()
            for ((key, value) in kvs) {
                if (key in Preferences.BACKUP_BLACKLIST) continue
                when (value) {
                    is Int -> jsonObject.put(key, "#i#$value")
                    is Float -> jsonObject.put(key, "#f#$value")
                    // Long 同样带前缀写成字符串。原先写成 JSON 数字, 而 JSONObject.get 会按数值大小
                    // 返回 Integer 或 Long, 类型在往返中会丢失, 恢复时可能把 Long 键写成 Int,
                    // 之后 getLong 读取就是 ClassCastException
                    is Long -> jsonObject.put(key, "#l#$value")
                    is String -> jsonObject.put(key, value)
                    is Boolean -> jsonObject.put(key, value)
                    is Set<*> -> jsonObject.put(key, value.joinToString(",", "[", "]"))
                    else -> jsonObject.put(key, value)
                }
            }

            outputStream.bufferedWriter().use { it.write(jsonObject.toString()) }
        }
    }

    suspend fun importBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Can't open input stream")

            val allText = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(allText)

            if (!checkBackupFileValid(jsonObject)) {
                throw IllegalArgumentException("Unsupported backup version or invalid file format")
            }

            val kvs = mutableMapOf<String, Any>()
            for (key in jsonObject.keys()) {
                if (key in Preferences.BACKUP_BLACKLIST) continue
                when (val value = jsonObject.get(key)) {
                    // is Long 是给旧版备份文件的兜底: 那时 Long 直接写成 JSON 数字。
                    // 原先这里既没有 is Long 也没有 else 分支, Long 类型的偏好会被静默丢弃
                    is Boolean, is Int, is Long, is Float -> kvs[key] = value
                    is String -> {
                        if (value.startsWith("[") && value.endsWith("]")) {
                            // 空集合导出后是 "[]", 去掉方括号就是空串。
                            // 原先直接 split(",") 会得到 listOf("")，让每个空列表都凭空多出一个空字符串条目
                            val inner = value.removeSurrounding("[", "]").replace(" ", "")
                            kvs[key] = inner.split(",").filter { it.isNotEmpty() }.toHashSet()
                        } else if (value.startsWith("#i#")) {
                            kvs[key] = value.removePrefix("#i#").toInt()
                        } else if (value.startsWith("#l#")) {
                            kvs[key] = value.removePrefix("#l#").toLong()
                        } else if (value.startsWith("#f#")) {
                            kvs[key] = value.removePrefix("#f#").toFloat()
                        } else {
                            kvs[key] = value
                        }
                    }
                }
            }
            prefStore.setAll(kvs)

            initAndCheck()
            _globalReloadEvent.emit(Unit)
        }
    }

    suspend fun resetSettings(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefStore.clearAll()
            initAndCheck()
            _globalReloadEvent.emit(Unit)
        }
    }

    private fun checkBackupFileValid(jsonObject: JSONObject): Boolean {
        val versionKey = Preferences.Module.SP_VERSION.name

        if (!jsonObject.has(versionKey)) {
            return true
        }

        return try {
            when (val value = jsonObject.get(versionKey)) {
                is Int -> true
                is String -> {
                    val version = value.removePrefix("#i#").toInt()
                    version <= Preferences.VERSION
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
