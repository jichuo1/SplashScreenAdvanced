package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.PreferenceKey
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeAppDraft
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeAppDraftSnapshot
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeContentViewport
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
import com.SplashScreenAdvanced.xposedmodule.utils.HanziToPinyin
import com.SplashScreenAdvanced.xposedmodule.utils.toMap
import com.SplashScreenAdvanced.xposedmodule.utils.toSet
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

fun NativePageUi.appList(checkedKey: PreferenceKey<Set<String>>? = null, durationEditing: Boolean = false,
                         colorBrowsing: Boolean = false, extra: (() -> View)? = null): View {
    val configKey = when {
        durationEditing -> Preferences.AppList.MIN_DURATION_CONFIG_MAP
        colorBrowsing && activity.isDark() -> Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK
        colorBrowsing -> Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP
        else -> null
    }
    fun currentConfig() = configKey?.let { repo.get(it).toMap().toMap() } ?: emptyMap()
    fun currentChecked() = checkedKey?.let { repo.get(it) } ?: currentConfig().keys
    val draft = NativeAppDraft(currentChecked(), currentConfig())
    restored?.getBundle("draft")?.let { saved ->
        fun entries(key: String) = saved.getStringArrayList(key)?.toSet().orEmpty()
        draft.restore(NativeAppDraftSnapshot(entries("savedChecked"), entries("savedConfig").toMap(),
            entries("checked"), entries("config").toMap()))
    }
    var apps = emptyList<InstalledApp>()
    var visible = emptyList<InstalledApp>()
    var query = restored?.getString("query").orEmpty()
    var filterJob: Job? = null
    val root = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
    val search = EditText(activity).apply {
        id = R.id.native_search; hint = string(R.string.search_hint); isSingleLine = true
        setTextColor(palette.textPrimary); setHintTextColor(palette.textSecondary)
        setText(query)
    }
    root.addView(search, LinearLayout.LayoutParams(-1, -2))
    val tools = LinearLayout(activity)
    if (!colorBrowsing) tools.addView(button("⋯") {
        dialog(string(R.string.select_system_apps)) { dialog, container ->
            container.addView(button(string(R.string.select_system_apps)) {
                activity.modals.dismiss(dialog, container) {
                    if (requireModule()) { draft.checked.addAll(apps.filter { it.isSystemApp }.map { it.packageName }); search.setText(query) }
                }
            })
            container.addView(button(string(R.string.clear_selected_apps)) {
                activity.modals.dismiss(dialog, container) {
                    if (requireModule()) { draft.checked.clear(); search.setText(query) }
                }
            })
        }
    }, LinearLayout.LayoutParams(dp(56), dp(48)))
    if (!colorBrowsing) tools.addView(button(string(R.string.save)) {
        val saved = checkedKey == null || write(checkedKey, draft.checked.toSet())
        val configSaved = configKey == null || write(configKey, draft.config.toMutableMap().toSet())
        if (saved && configSaved) { draft.markSaved(); activity.toast(R.string.save_successful) }
    }.apply { id = R.id.native_save }, LinearLayout.LayoutParams(0, dp(48), 1f))
    root.addView(tools)
    if (extra != null) root.addView(extra())
    if (durationEditing) root.addView(action(string(R.string.set_default_min_duration), moduleRequired = false) { row ->
        numberDialog(row.titleView.text.toString(), string(R.string.set_min_duration_unit),
            repo.get(Preferences.Display.MIN_DURATION), anchor = row) { value ->
            if (value != null) { write(Preferences.Display.MIN_DURATION, value); refresh() }
        }
    })
    root.addView(text(string(if (durationEditing) R.string.min_duration_sub_setting_hint
        else if (colorBrowsing) R.string.custom_bg_color_sub_setting_hint else R.string.save_hint), 13f, secondary = true))
    val loading = text(string(R.string.loading), 14f, secondary = true)
    root.addView(loading)
    val list = ListView(activity).apply {
        id = R.id.native_list; divider = null; dividerHeight = dp(8)
        clipToPadding = false; setPadding(0, dp(8), 0, dp(8))
    }
    lateinit var adapter: BaseAdapter
    fun filter() {
        filterJob?.cancel()
        val selection = draft.checked.toSet()
        val configured = draft.config.keys.toSet()
        val all = apps
        val text = query
        filterJob = activity.uiScope.launch {
            delay(if (text.isBlank()) 40 else 250)
            visible = withContext(Dispatchers.Default) {
                val collator = Collator.getInstance(Locale.getDefault())
                all.filter { text.isBlank() || it.appName.contains(text, true) || it.packageName.contains(text, true) ||
                    HanziToPinyin.toPinyin(it.appName).contains(text, true) }.sortedWith(
                    if (colorBrowsing) compareBy(collator) { it.appName }
                    else compareByDescending<InstalledApp> { it.packageName in selection }
                        .thenByDescending { durationEditing && it.packageName in configured }.thenBy(collator) { it.appName })
            }
            adapter.notifyDataSetChanged()
        }
    }
    adapter = object : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int) = visible[position]
        override fun getItemId(position: Int) = apps.indexOf(visible[position]).toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)
            val row = (convertView as? NativeAppRow) ?: NativeAppRow(this@appList, colorBrowsing)
            row.titleView.text = item.appName
            row.summaryView.text = item.packageName
            row.summaryView.visibility = View.VISIBLE
            row.valueView.text = if (durationEditing) draft.config[item.packageName] ?: string(R.string.not_set_min_duration)
                else if (colorBrowsing) draft.config[item.packageName].orEmpty() else ""
            activity.bindAppIcon(row.icon, item.packageName)
            row.toggle.setOnCheckedChangeListener(null)
            row.toggle.isChecked = item.packageName in draft.checked
            var rebinding = false
            row.toggle.setOnCheckedChangeListener { _, checked ->
                if (rebinding) return@setOnCheckedChangeListener
                if (requireModule()) { draft.setChecked(item.packageName, checked); filter() }
                else { rebinding = true; row.toggle.isChecked = item.packageName in draft.checked; rebinding = false }
            }
            row.setOnClickListener {
                if (requireModule()) when {
                    colorBrowsing -> activity.navigate(Route.ColorPicker(item.packageName), row, row.titleView)
                    durationEditing -> numberDialog(item.appName, string(R.string.set_min_duration_unit),
                        draft.config[item.packageName]?.toIntOrNull(), anchor = row) {
                        draft.setConfig(item.packageName, it?.toString()); filter()
                    }
                    else -> row.toggle.toggle()
                }
            }
            return row
        }
    }
    list.adapter = adapter
    root.addView(NativeContentViewport(activity).apply {
        addView(list, FrameLayout.LayoutParams(-1, -1))
    }, LinearLayout.LayoutParams(-1, 0, 1f))
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); filter() }
        override fun afterTextChanged(s: Editable?) = Unit
    })
    if (!colorBrowsing) stageChanges({ draft.isDirty }, { draft.discard(); filter() }, {
        draft.reload(currentChecked(), currentConfig()); filter()
    })
    bind {
        if (colorBrowsing) { draft.reload(currentChecked(), currentConfig()); filter() }
        else adapter.notifyDataSetChanged()
    }
    addStateWriter { result ->
        val snapshot = draft.snapshot()
        result.putString("query", query)
        result.putBundle("draft", Bundle().apply {
            putStringArrayList("savedChecked", ArrayList(snapshot.savedChecked))
            putStringArrayList("savedConfig", ArrayList(snapshot.savedConfig.toMutableMap().toSet()))
            putStringArrayList("checked", ArrayList(snapshot.checked))
            putStringArrayList("config", ArrayList(snapshot.config.toMutableMap().toSet()))
        })
        result.putInt("listPosition", list.firstVisiblePosition)
        result.putInt("listOffset", list.getChildAt(0)?.top ?: 0)
    }
    activity.uiScope.launch {
        apps = loadInstalledApps(activity)
        loading.visibility = View.GONE
        filter()
        filterJob?.join()
        list.setSelectionFromTop(restored?.getInt("listPosition") ?: 0, restored?.getInt("listOffset") ?: 0)
    }
    return root
}

private class NativeAppRow(ui: NativePageUi, colorBrowsing: Boolean) : NativeRow(ui.activity, "", "") {
    val icon = ImageView(ui.activity)
    val toggle = CheckBox(ui.activity).apply { buttonTintList = ColorStateList.valueOf(ui.palette.primary) }
    init {
        background = ui.lumen.cardBackground(ui.palette.surface, 16f)
        addView(icon, 0, LayoutParams(ui.dp(40), ui.dp(40)).apply { marginEnd = ui.dp(12) })
        if (colorBrowsing) toggle.visibility = View.GONE
        else tag = ElasticInteractionController.EXCLUDED_TAG
        addView(toggle, LayoutParams(-2, -2))
    }
}
