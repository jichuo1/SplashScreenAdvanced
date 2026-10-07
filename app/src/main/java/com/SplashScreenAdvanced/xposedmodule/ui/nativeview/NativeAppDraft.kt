package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

data class NativeAppDraftSnapshot(
    val savedChecked: Set<String>, val savedConfig: Map<String, String>,
    val checked: Set<String>, val config: Map<String, String>,
)

/** Edits remain local until Save; a snapshot retains both the entry baseline and current draft. */
class NativeAppDraft(checked: Set<String>, config: Map<String, String> = emptyMap()) {
    private var savedChecked = checked.toSet()
    private var savedConfig = config.toMap()
    val checked = checked.toMutableSet()
    val config = config.toMutableMap()
    val isDirty: Boolean get() = checked != savedChecked || config != savedConfig
    fun setChecked(packageName: String, enabled: Boolean) { if (enabled) checked.add(packageName) else checked.remove(packageName) }
    fun setConfig(packageName: String, value: String?) { if (value == null) config.remove(packageName) else config[packageName] = value }
    fun discard() { checked.clear(); checked.addAll(savedChecked); config.clear(); config.putAll(savedConfig) }
    fun markSaved() { savedChecked = checked.toSet(); savedConfig = config.toMap() }
    fun reload(checked: Set<String>, config: Map<String, String>) {
        savedChecked = checked.toSet(); savedConfig = config.toMap(); discard()
    }
    fun snapshot() = NativeAppDraftSnapshot(savedChecked.toSet(), savedConfig.toMap(), checked.toSet(), config.toMap())
    fun restore(snapshot: NativeAppDraftSnapshot) {
        savedChecked = snapshot.savedChecked.toSet(); savedConfig = snapshot.savedConfig.toMap()
        checked.clear(); checked.addAll(snapshot.checked); config.clear(); config.putAll(snapshot.config)
    }
}
