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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.text.InputType
import androidx.annotation.DrawableRes
import androidx.core.widget.NestedScrollView
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.PreferenceKey
import com.SplashScreenAdvanced.xposedmodule.ui.component.observeNativeUpdates
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsSection
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import io.github.jichuo1.meguviz.android.extensions.textColor
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import com.lumen.coacervation.engine.motion.expansion.SectionExpansionController
import com.lumen.coacervation.engine.motion.pager.LumenPageScrollView
import com.lumen.coacervation.engine.widget.CoverableRippleDrawable
import com.lumen.coacervation.engine.widget.LumenMaxHeightScrollView
import com.lumen.coacervation.engine.widget.NavigationBarSelectionMask
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
    var stateNamespace = ""
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

    /**
     * Rows that only apply while their parent switch or choice is on. They expand and collapse with the engine's accordion
     * (standard §13.7): one spring drives the reveal, the rows' cascade and the siblings sliding below, so the host never
     * animates heights itself. The group has no header, hence the stand-in chevron the controller asks for.
     */
    fun conditional(condition: () -> Boolean, build: LinearLayout.() -> Unit): View {
        val content = column().apply { build(); visibility = View.GONE }
        val chevron = View(activity)
        val card = AccordionCard(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(content, LinearLayout.LayoutParams(-1, -2))
            addView(chevron, LinearLayout.LayoutParams(0, 0))
        }
        val controller = SectionExpansionController(card, content, chevron, activity.resources.displayMetrics.density,
            notifyPositionChanged = { lumen.notifyPositionChanged() })
        var expanded = false
        var settled = false
        bind {
            val wanted = condition()
            // The first evaluation sets the initial state; later changes animate.
            if (!settled || wanted != expanded) { controller.setExpanded(wanted, animate = settled); expanded = wanted; settled = true }
        }
        return card
    }

    private val pageScrolls = mutableListOf<LumenPageScrollView>()

    /** Page scroller of the engine (standard §13.6): reports user scrolls, keyboard paging and a11y scrolls as navigation. */
    fun scrollContent(stateKey: String = "scrollY", build: LinearLayout.() -> Unit = {}): NestedScrollView =
        LumenPageScrollView(activity, onUserScroll = { activity.cancelReveal() }, onContentTouch = {}).apply {
            id = if (stateKey == "masterScroll") R.id.native_master_scroll else R.id.native_scroll
            val namespacedKey = stateNamespace + stateKey
            isFillViewport = true
            clipToPadding = false
            clipChildren = false
            addView(column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)); build() })
            // Every real scroll moves the content beneath the floating bars; the engine re-samples it (standard §4).
            onScrollPositionChanged = { view -> if (!released) lumen.notifyScrollPositionChanged(view) }
            pageScrolls += this
            addStateWriter { it.putInt(namespacedKey, scrollY) }
            post { scrollTo(0, restored?.getInt(namespacedKey) ?: 0) }
        }

    fun LinearLayout.addSetting(view: View) {
        // An accordion group carries no margin of its own: its rows keep theirs, so a collapsed group leaves no gap.
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (view is AccordionCard) 0 else dp(10) })
    }

    /** A row that settings search can reveal; [sequence] is the order rows were registered in. */
    class SettingsTarget(val titleRes: Int, val section: SettingsSection, val view: View, val sequence: Int)

    private val targets = LinkedHashMap<Int, SettingsTarget>()
    private var activeSection: SettingsSection? = null
    val searchTargets: Collection<SettingsTarget> get() = targets.values
    fun target(titleResource: Int): SettingsTarget? = targets[titleResource]

    /** Registers the row for search; only rows built inside a [section] are tracked, the first title wins. */
    fun <V : View> V.tracked(titleResource: Int): V = also { view ->
        val section = activeSection ?: return@also
        if (titleResource !in targets) targets[titleResource] = SettingsTarget(titleResource, section, view, targets.size)
    }

    /**
     * Titled group of rows. A section that ends up with no rows (a platform-only group on another
     * platform) adds nothing, so no heading is ever left dangling.
     */
    fun LinearLayout.section(section: SettingsSection, build: LinearLayout.() -> Unit) {
        val body = column()
        val outer = activeSection
        activeSection = section
        try { body.build() } finally { activeSection = outer }
        if (body.childCount == 0) return
        addView(sectionTitle(string(section.titleRes)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10); bottomMargin = dp(8)
        })
        addView(body, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
    }

    private fun sectionTitle(value: String): TextView = text(value, 13f, bold = true).apply {
        textColor = palette.primary
        alpha = .9f
        setPaddingRelative(dp(6), 0, dp(6), 0)
        isAccessibilityHeading = true
    }

    fun bind(update: () -> Unit) { bindings += update; update() }
    fun refresh() { if (!released) bindings.forEach { it() } }
    fun addStateWriter(write: (Bundle) -> Unit) { stateWriters += write }
    fun saveState(): Bundle = Bundle().also { result -> stateWriters.forEach { it(result) } }
    fun stageChanges(hasChanges: () -> Boolean, discardChanges: () -> Unit, onReload: () -> Unit = discardChanges) {
        dirty = hasChanges; discard = discardChanges; reloadDraft = onReload
    }
    fun onPreferencesReloaded() { if (!hasUnsavedChanges) reloadDraft(); refresh() }
    fun dispose() {
        released = true
        pageScrolls.forEach { it.onScrollPositionChanged = null }
        pageScrolls.clear()
        bindings.clear(); stateWriters.clear(); visibleWork.clear()
    }
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
        background = activity.meguviz.own(lumen.cardBackground(palette.surface, 16f))
        foreground = activity.meguviz.own(CoverableRippleDrawable.rounded(palette, dp(16).toFloat()))
        clipChildren = false
        clipToPadding = false
    }

    fun action(title: String, summary: String? = null, moduleRequired: Boolean = true,
               onClick: (NativeRow) -> Unit): NativeRow = row(title, summary).apply {
        isClickable = true
        setOnClickListener { if (requireModule(moduleRequired)) onClick(this) }
    }

    fun navigation(titleResource: Int, route: Route, summaryResource: Int? = null,
                   moduleRequired: Boolean = true, searchable: Boolean = true): View =
        action(string(titleResource), summaryResource?.let(::string), moduleRequired) {
            activity.navigate(route, it, it.titleView)
        }.also { if (searchable) it.tracked(titleResource) }

    fun switch(titleResource: Int, key: PreferenceKey<Boolean>, summaryResource: Int? = null,
               moduleRequired: Boolean = true, enabled: () -> Boolean = { true }, after: (Boolean) -> Unit = {}): View {
        val row = row(string(titleResource), summaryResource?.let(::string))
        row.tag = ElasticInteractionController.EXCLUDED_TAG
        val toggle = engineSwitch(Switch(activity).apply {
            contentDescription = string(titleResource)
            isSaveEnabled = false
        })
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
        return row.tracked(titleResource)
    }

    fun <T : Any> choice(titleResource: Int, entries: () -> List<NativeChoice<T>>, current: () -> T,
                         summaryResource: Int? = null, moduleRequired: Boolean = true,
                         selected: (T) -> Unit): View {
        val row = action(string(titleResource), summaryResource?.let(::string), moduleRequired) { source ->
            dialog(string(titleResource), summaryResource?.let(::string), source) { dialog, container ->
                // Room for the selected item's outline, which draws at the very edge of its bounds.
                val options = column().apply { setPadding(0, dp(3), 0, dp(3)) }
                entries().forEach { entry ->
                    val option = TextView(activity).apply {
                        text = entry.title + (entry.summary?.let { "\n$it" } ?: "")
                        textSize = 16f
                        setTextColor(palette.textPrimary)
                        minHeight = dp(52)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(16), dp(10), dp(16), dp(10))
                        isClickable = true
                        setOnClickListener { activity.modals.dismiss(dialog, container) { selected(entry.value); refresh() } }
                        // The engine's selectable item: the current choice sits on the selected surface, the rest on cards.
                        lumen.styleSelectionControl(this, 14f, entry.value == current())
                    }
                    options.addView(option, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                }
                // The engine's capped scroller: the list grows with its entries up to 420dp, then scrolls.
                container.addView(LumenMaxHeightScrollView(activity, dp(420)).apply { addView(options) },
                    LinearLayout.LayoutParams(-1, -2))
            }
        }
        bind { row.valueView.text = entries().firstOrNull { it.value == current() }?.title.orEmpty() }
        return row.tracked(titleResource)
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

    /**
     * Round glass button for the floating top capsule: a 48dp circle on the engine's chrome overlay surface,
     * same construction as BIL's toolbar buttons. It is a companion of the capsule for legibility compensation.
     */
    fun iconButton(@DrawableRes icon: Int, description: String, clicked: () -> Unit): ImageView = ImageView(activity).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.textSecondary)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        // A light contrast mask keeps the circle readable on SSA's near-white surface.
        background = activity.meguviz.own(lumen.chromeOverlayBackground(NavigationBarSelectionMask.color(palette, .07f), 24f))
        foreground = activity.meguviz.own(CoverableRippleDrawable.rounded(palette, dp(24).toFloat()))
        contentDescription = description
        isClickable = true
        setOnClickListener { clicked() }
    }

    /** Text button on the engine's own action-button recipe: glass as the background, the ripple in the foreground. */
    fun button(label: String, clicked: () -> Unit): Button = Button(activity).apply {
        text = label
        setOnClickListener { clicked() }
        lumen.styleActionButton(this, filled = false)
    }

    fun numberDialog(title: String, message: String?, current: Int?, min: Int = 0, max: Int = Int.MAX_VALUE,
                     anchor: NativeRow? = null, chosen: (Int?) -> Unit) = dialog(title, message, anchor) { dialog, container ->
        val input = engineEditText(EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            setTextColor(palette.textPrimary)
            setSelectAllOnFocus(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        })
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

/** Wrapper of a collapsible group of dependent rows; see [NativePageUi.conditional]. */
internal class AccordionCard(context: android.content.Context) : LinearLayout(context)

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
