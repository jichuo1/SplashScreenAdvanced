package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.text.InputType
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.PreferenceKey
import com.SplashScreenAdvanced.xposedmodule.ui.component.observeNativeUpdates
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import com.lumen.coacervation.engine.model.SurfaceRole
import com.lumen.coacervation.engine.widget.CoverableRippleDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class NativeChoice<T>(val value: T, val title: String, val summary: String? = null)

/** Native widgets share one binding and dialog implementation across every settings page. */
class NativePageUi(val activity: NativeSettingsActivity, val restored: Bundle?) {
    val repo get() = activity.repository
    val lumen get() = activity.lumen
    val palette get() = lumen.palette
    var backButton: View? = null
    private val bindings = mutableListOf<() -> Unit>()
    private val stateWriters = mutableListOf<(Bundle) -> Unit>()
    private val visibleWork = mutableListOf<suspend () -> Unit>()
    private var dirty: () -> Boolean = { false }
    private var discard: () -> Unit = {}
    private var reloadDraft: () -> Unit = {}
    private var released = false
    val hasUnsavedChanges get() = dirty()
    fun dp(value: Int) = activity.dp(value)
    fun string(resource: Int, vararg args: Any): String = activity.getString(resource, *args)
    fun text(value: String, size: Float = 16f, secondary: Boolean = false, bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(if (secondary) palette.textSecondary else palette.textPrimary)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun column(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    fun conditional(condition: () -> Boolean, build: LinearLayout.() -> Unit): View = column().apply {
        build()
        bind { visibility = if (condition()) View.VISIBLE else View.GONE }
    }

    fun scrollContent(stateKey: String = "scrollY", build: LinearLayout.() -> Unit = {}): ScrollView = ScrollView(activity).apply {
        id = if (stateKey == "masterScroll") R.id.native_master_scroll else R.id.native_scroll
        isFillViewport = true
        clipToPadding = false
        clipChildren = false
        addView(column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)); build() })
        addStateWriter { it.putInt(stateKey, scrollY) }
        post { scrollTo(0, restored?.getInt(stateKey) ?: 0) }
    }

    fun LinearLayout.addSetting(view: View) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    fun bind(update: () -> Unit) { bindings += update; update() }
    fun refresh() { if (!released) bindings.forEach { it() } }
    fun addStateWriter(write: (Bundle) -> Unit) { stateWriters += write }
    fun saveState(): Bundle = Bundle().also { result -> stateWriters.forEach { it(result) } }
    fun stageChanges(hasChanges: () -> Boolean, discardChanges: () -> Unit, onReload: () -> Unit = discardChanges) {
        dirty = hasChanges; discard = discardChanges; reloadDraft = onReload
    }
    fun onPreferencesReloaded() { if (!hasUnsavedChanges) reloadDraft(); refresh() }
    fun dispose() { released = true; bindings.clear(); stateWriters.clear(); visibleWork.clear() }
    fun whileVisible(work: suspend () -> Unit) { visibleWork += work }
    suspend fun observeUpdates() = coroutineScope {
        launch { observeNativeUpdates() }
        visibleWork.forEach { work -> launch { work() } }
    }

    fun confirmDiscard(onDiscarded: () -> Unit): Boolean {
        if (!hasUnsavedChanges) return false
        dialog(string(R.string.not_saved_title), string(R.string.not_saved_hint)) { dialog, container ->
            container.addView(button(string(R.string.button_abandonment)) {
                activity.modals.dismiss(dialog, container) { discard(); onDiscarded() }
            })
            container.addView(button(string(R.string.button_reedit)) { activity.modals.dismiss(dialog, container) })
        }
        return true
    }

    fun row(title: String, summary: String? = null): NativeRow = NativeRow(activity, title, summary).apply {
        background = lumen.cardBackground(palette.surface, 16f)
        foreground = CoverableRippleDrawable.rounded(palette, dp(16).toFloat())
        clipChildren = false
        clipToPadding = false
    }

    fun action(title: String, summary: String? = null, moduleRequired: Boolean = true,
               onClick: (NativeRow) -> Unit): NativeRow = row(title, summary).apply {
        isClickable = true
        setOnClickListener { if (requireModule(moduleRequired)) onClick(this) }
    }

    fun navigation(titleResource: Int, route: Route, summaryResource: Int? = null,
                   moduleRequired: Boolean = true): View = action(string(titleResource), summaryResource?.let(::string), moduleRequired) {
        activity.navigate(route, it, it.titleView)
    }

    fun switch(titleResource: Int, key: PreferenceKey<Boolean>, summaryResource: Int? = null,
               moduleRequired: Boolean = true, enabled: () -> Boolean = { true }, after: (Boolean) -> Unit = {}): View {
        val row = row(string(titleResource), summaryResource?.let(::string))
        row.tag = ElasticInteractionController.EXCLUDED_TAG
        val toggle = Switch(activity).apply {
            contentDescription = string(titleResource)
            isSaveEnabled = false
            thumbTintList = ColorStateList.valueOf(palette.primary)
        }
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        var rebinding = false
        toggle.setOnCheckedChangeListener { _, checked ->
            if (!rebinding) {
                if (enabled() && requireModule(moduleRequired) && write(key, checked)) after(checked)
                rebinding = true; toggle.isChecked = repo.get(key); rebinding = false
                refresh()
            }
        }
        row.setOnClickListener { if (enabled()) toggle.toggle() }
        bind {
            rebinding = true; toggle.isChecked = repo.get(key); rebinding = false
            row.isEnabled = enabled(); toggle.isEnabled = enabled(); row.alpha = if (enabled()) 1f else .45f
        }
        return row
    }

    fun <T : Any> choice(titleResource: Int, entries: () -> List<NativeChoice<T>>, current: () -> T,
                         summaryResource: Int? = null, moduleRequired: Boolean = true,
                         selected: (T) -> Unit): View {
        val row = action(string(titleResource), summaryResource?.let(::string), moduleRequired) { source ->
            dialog(string(titleResource), summaryResource?.let(::string), source) { dialog, container ->
                val options = column()
                entries().forEach { entry ->
                    val option = RadioButton(activity).apply {
                        text = entry.title + (entry.summary?.let { "\n$it" } ?: "")
                        setTextColor(palette.textPrimary)
                        buttonTintList = ColorStateList.valueOf(palette.primary)
                        isChecked = entry.value == current()
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        setOnClickListener { activity.modals.dismiss(dialog, container) { selected(entry.value); refresh() } }
                    }
                    options.addView(option, LinearLayout.LayoutParams(-1, -2))
                }
                container.addView(ScrollView(activity).apply { addView(options) },
                    LinearLayout.LayoutParams(-1, -2).apply { height = minOf(dp(420), dp(58) * entries().size) })
            }
        }
        bind { row.valueView.text = entries().firstOrNull { it.value == current() }?.title.orEmpty() }
        return row
    }

    fun intChoice(titleResource: Int, key: PreferenceKey<Int>, titles: List<Int>, summaryResource: Int? = null,
                  moduleRequired: Boolean = true, after: (Int) -> Unit = {}): View = choice(titleResource,
        { titles.mapIndexed { index, title -> NativeChoice(index, string(title)) } }, { repo.get(key) },
        summaryResource, moduleRequired) { if (write(key, it)) after(it) }

    fun <T : Any> write(key: PreferenceKey<T>, value: T): Boolean = try {
        repo.update(key, value)
        true
    } catch (_: Exception) { activity.toast(R.string.save_failed); false }

    fun requireModule(required: Boolean = true): Boolean {
        if (!required || activity.uiState.moduleActive) return true
        activity.toast(R.string.make_sure_active)
        return false
    }

    fun actionButton(label: String, description: String, clicked: () -> Unit) = text(label, 26f).apply {
        gravity = Gravity.CENTER
        contentDescription = description
        foreground = CoverableRippleDrawable.rounded(palette, dp(24).toFloat())
        setOnClickListener { clicked() }
    }

    fun button(label: String, clicked: () -> Unit): Button = Button(activity).apply {
        text = label
        setTextColor(palette.primary)
        background = lumen.surface(palette.surface, 12f, SurfaceRole.TEXT_BUTTON)
        foreground = CoverableRippleDrawable.rounded(palette, dp(12).toFloat())
        setOnClickListener { clicked() }
    }

    fun numberDialog(title: String, message: String?, current: Int?, min: Int = 0, max: Int = Int.MAX_VALUE,
                     anchor: NativeRow? = null, chosen: (Int?) -> Unit) = dialog(title, message, anchor) { dialog, container ->
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            setTextColor(palette.textPrimary)
            setSelectAllOnFocus(true)
        }
        container.addView(input, LinearLayout.LayoutParams(-1, -2))
        container.addView(button(string(android.R.string.ok)) {
            val raw = input.text.toString()
            val value = raw.toIntOrNull()
            if (raw.isNotBlank() && (value == null || value !in min..max)) input.error = "$min..$max"
            else activity.modals.dismiss(dialog, container) { chosen(value) }
        })
        container.addView(button(string(R.string.button_cancel)) { activity.modals.dismiss(dialog, container) })
    }

    fun dialog(title: String, message: String? = null, anchor: NativeRow? = null, onDismissed: () -> Unit = {},
               build: (Dialog, LinearLayout) -> Unit) {
        val dialog = Dialog(activity)
        val container = activity.modals.createContainer()
        val sourceTitle = anchor?.titleView?.text?.toString()
        container.addView(text(sourceTitle ?: title, 20f, bold = true))
        if (sourceTitle != null && sourceTitle != title) container.addView(text(title, 14f, secondary = true))
        if (!message.isNullOrBlank()) container.addView(text(message, 14f, secondary = true).apply {
            setPadding(0, dp(12), 0, dp(16))
        })
        build(dialog, container)
        activity.modals.present(dialog, container, anchor = anchor, onBackDismiss = onDismissed)
    }

    fun openUrl(url: String) {
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { activity.toast(R.string.no_browser) }
    }

    fun showRestartDialog() = dialog(string(R.string.restart_title), string(R.string.restart_message)) { dialog, container ->
        fun execute(command: String) { activity.modals.dismiss(dialog, container) {
            activity.uiScope.launch { withContext(Dispatchers.IO) { execShell(command) }; delay(300); activity.toast(R.string.no_root) }
        } }
        container.addView(button(string(R.string.reboot)) { execute("reboot") })
        container.addView(button(string(R.string.restart_system_ui)) {
            execute("pkill -f com.android.systemui && pkill -f com.SplashScreenAdvanced.xposedmodule")
        })
        container.addView(button(string(R.string.button_cancel)) { activity.modals.dismiss(dialog, container) })
    }
}

open class NativeRow(activity: NativeSettingsActivity, title: String, summary: String?) : LinearLayout(activity) {
    val titleView = TextView(activity).apply {
        text = title; textSize = 17f; setTextColor(activity.lumen.palette.textPrimary)
        setTypeface(typeface, Typeface.BOLD)
    }
    val summaryView = TextView(activity).apply {
        text = summary; textSize = 13f; setTextColor(activity.lumen.palette.textSecondary)
        visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        setPadding(0, activity.dp(4), 0, 0)
    }
    val valueView = TextView(activity).apply {
        textSize = 13f; setTextColor(activity.lumen.palette.textSecondary)
        maxWidth = activity.dp(140); gravity = Gravity.END
        setPadding(activity.dp(12), 0, 0, 0)
    }
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16))
        addView(LinearLayout(activity).apply { orientation = VERTICAL; addView(titleView); addView(summaryView) },
            LayoutParams(0, -2, 1f))
        addView(valueView, LayoutParams(-2, -2))
    }
}
