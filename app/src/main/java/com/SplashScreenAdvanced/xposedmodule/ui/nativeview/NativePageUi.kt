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
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsTab
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import io.github.jichuo1.meguviz.android.extensions.textColor
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import com.lumen.coacervation.engine.motion.expansion.SectionExpansionController
import com.lumen.coacervation.engine.motion.pager.LumenPageScrollView
import com.lumen.coacervation.engine.widget.CoverableRippleDrawable
import com.lumen.coacervation.engine.widget.LumenMaxHeightScrollView
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
    private val gatedWork = TabGatedWork()

    /** The dock tab on screen; null when this page stands alone (a routed page). */
    val shownTab get() = gatedWork.shownTab
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
        val content = DividedColumn(activity).apply { build(); visibility = View.GONE }
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
            // Explicitly wrap_content: the default would be match_parent, and the engine accordion stops looking for the
            // scroll view at the first ancestor that cannot shrink, so a collapse would not scroll along and the page
            // would jump once the animation lays out. isFillViewport still stretches a short page.
            addView(column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)); build() },
                android.widget.FrameLayout.LayoutParams(-1, -2))
            // Every real scroll moves the content beneath the floating bars; the engine re-samples it (standard §4).
            onScrollPositionChanged = { view -> if (!released) lumen.notifyScrollPositionChanged(view) }
            pageScrolls += this
            addStateWriter { it.putInt(namespacedKey, scrollY) }
            post { scrollTo(0, restored?.getInt(namespacedKey) ?: 0) }
        }

    fun LinearLayout.addSetting(view: View) {
        // An accordion group carries no margin of its own: its rows keep theirs, so a collapsed group leaves no gap.
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (view is AccordionCard) 0 else dp(ROW_GAP_DP) })
    }

    /**
     * A row that folds a long list of secondary rows (licenses, rarely used entries) behind a chevron, on the same engine
     * accordion as [conditional]. It starts collapsed so the page stays short; the state survives recreation.
     */
    fun expandable(title: String, summary: String? = null, value: String? = null, stateKey: String,
                   build: LinearLayout.() -> Unit): View {
        val key = stateNamespace + "expanded." + stateKey
        val header = row(title, summary)
        header.valueView.text = value
        val chevron = ImageView(activity).apply {
            setImageResource(R.drawable.ic_expand)
            imageTintList = ColorStateList.valueOf(palette.textSecondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(chevron, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(12) })
        val content = DividedColumn(activity).apply { build(); visibility = View.GONE }
        val card = AccordionCard(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ROW_GAP_DP) })
            addView(content, LinearLayout.LayoutParams(-1, -2))
        }
        val controller = SectionExpansionController(card, content, chevron, activity.resources.displayMetrics.density,
            notifyPositionChanged = { lumen.notifyPositionChanged() })
        var expanded = restored?.getBoolean(key) ?: false
        controller.setExpanded(expanded, animate = false)
        header.isClickable = true
        header.setOnClickListener { expanded = !expanded; controller.setExpanded(expanded) }
        addStateWriter { it.putBoolean(key, expanded) }
        return card
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
        val body = DividedColumn(activity)
        val outer = activeSection
        activeSection = section
        try { body.build() } finally { activeSection = outer }
        if (body.childCount == 0) return
        // One card per group with hairlines between its rows (BIL's settings cards), instead of a stack of separate
        // slabs: a single glass surface per group also samples once instead of once per row.
        adoptIntoGroup(body)
        body.background = lumen.cardBackground(palette.surfaceVariant, GROUP_RADIUS_DP)
        // Ripples and the accordion's folding rows stay inside the rounded card. The shape is explicit: not every
        // material's drawable reports an outline, and an empty one would not clip at all.
        val radius = dp(GROUP_RADIUS_DP.toInt()).toFloat()
        body.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        body.clipToOutline = true
        // Groups are told apart by air above the caption (BIL: 20dp above, 6dp below).
        addView(sectionTitle(string(section.titleRes)), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(if (childCount == 0) 4 else 18); bottomMargin = dp(8)
        })
        addView(body, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
    }

    /**
     * Turns the rows built for a section into cells of its card: they lose their own card and the gaps between them,
     * and every column on the way (the group, accordions and their contents) draws the inset hairlines.
     *
     * Dropping the row backgrounds also moves the long-press elasticity to the whole group: the engine promotes a
     * pressed view to the nearest ancestor that owns a surface and unifies several children
     * (`ElasticMotionGroupPolicy.promotionDepth`), which is now the group card rather than the row clipped inside it.
     */
    private fun adoptIntoGroup(column: DividedColumn) {
        column.drawsDividers = true
        column.dividerColor = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.textSecondary, DIVIDER_ALPHA)
        // Lines start where the row text starts and stop short of the trailing control, like a grouped list.
        column.dividerInsetStart = dp(18)
        column.dividerInsetEnd = dp(16)
        for (index in 0 until column.childCount) {
            val child = column.getChildAt(index)
            (child.layoutParams as? LinearLayout.LayoutParams)?.let {
                if (it.topMargin != 0 || it.bottomMargin != 0) { it.topMargin = 0; it.bottomMargin = 0; child.layoutParams = it }
            }
            if (child is DividedColumn) { adoptIntoGroup(child); continue }
            child.background = null
            if (child is NativeRow) child.foreground = activity.meguviz.own(CoverableRippleDrawable.rounded(palette, 0f))
        }
    }

    private fun sectionTitle(value: String): TextView = text(value, 13f, bold = true).apply {
        textColor = palette.primary
        alpha = .9f
        letterSpacing = .02f
        setPaddingRelative(dp(8), 0, dp(8), 0)
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
        bindings.clear(); stateWriters.clear(); gatedWork.clear()
    }

    /** Work that only matters while [tab] is on screen: it runs while the activity is resumed and the tab is shown. */
    fun whileVisible(tab: SettingsTab, work: suspend () -> Unit) = gatedWork.add(tab, work)
    suspend fun observeUpdates() = coroutineScope {
        launch { observeNativeUpdates() }
        launch { gatedWork.run() }
    }

    fun confirmDiscard(onDiscarded: () -> Unit): Boolean {
        if (!hasUnsavedChanges) return false
        dialog(string(R.string.not_saved_title), string(R.string.not_saved_hint)) { dialog, container ->
            dialogActions(container,
                button(string(R.string.button_abandonment)) {
                    activity.modals.dismiss(dialog, container) { discard(); onDiscarded() }
                },
                button(string(R.string.button_reedit), filled = true) { activity.modals.dismiss(dialog, container) })
        }
        return true
    }

    fun row(title: String, summary: String? = null): NativeRow = NativeRow(activity, title, summary).apply {
        background = lumen.cardBackground(palette.surfaceVariant, 16f)
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
                        // The summary reads as a caption under the title, not as a second line of the same weight.
                        text = android.text.SpannableStringBuilder(entry.title).apply {
                            val summary = entry.summary ?: return@apply
                            append('\n')
                            val start = length
                            append(summary)
                            setSpan(android.text.style.AbsoluteSizeSpan(13, true), start, length, 0)
                            setSpan(android.text.style.ForegroundColorSpan(palette.textSecondary), start, length, 0)
                        }
                        textSize = 16f
                        setTextColor(palette.textPrimary)
                        setLineSpacing(dp(2).toFloat(), 1f)
                        minHeight = dp(52)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                        isClickable = true
                        setOnClickListener { activity.modals.dismiss(dialog, container) { selected(entry.value); refresh() } }
                        // The engine's selectable item: the current choice sits on the selected surface, the rest on cards.
                        lumen.styleSelectionControl(this, 14f, entry.value == current())
                    }
                    options.addView(option, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                }
                // The engine's capped scroller: the list grows with its entries up to 420dp, then scrolls.
                container.addView(LumenMaxHeightScrollView(activity, dp(420)).apply { addView(options) },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
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
        // BIL's toolbar button: surface-coloured chrome lens, 10dp padding (a 28dp glyph), primary-text tint.
        imageTintList = ColorStateList.valueOf(palette.textPrimary)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = lumen.chromeOverlayBackground(palette.surface, 24f)
        foreground = activity.meguviz.own(CoverableRippleDrawable.rounded(palette, dp(24).toFloat()))
        contentDescription = description
        isClickable = true
        setOnClickListener { clicked() }
    }

    /**
     * Text button on the engine's own action-button recipe: glass as the background, the ripple in the foreground.
     * The framework Button's elevation animator is dropped: the system shadow of an opaque box shows as a grey ring
     * through translucent glass. Caps and the oversized default insets go too.
     */
    fun button(label: String, filled: Boolean = false, clicked: () -> Unit): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        minHeight = dp(BUTTON_HEIGHT_DP); minimumHeight = dp(BUTTON_HEIGHT_DP)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(16), 0, dp(16), 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setOnClickListener { clicked() }
        lumen.styleActionButton(this, filled = filled)
        if (filled) setTextColor(palette.primary)
    }

    /**
     * Buttons at the foot of a dialog or a page: side by side with an 8dp gutter (the confirming one last), or stacked
     * when there are more than two or their labels are long.
     */
    fun buttonRow(vararg buttons: Button, stacked: Boolean = buttons.size > 2): LinearLayout =
        LinearLayout(activity).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            buttons.forEachIndexed { index, button ->
                addView(button, if (stacked) LinearLayout.LayoutParams(-1, dp(BUTTON_HEIGHT_DP)).apply {
                    if (index > 0) topMargin = dp(8)
                } else LinearLayout.LayoutParams(0, dp(BUTTON_HEIGHT_DP), 1f).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
        }

    fun dialogActions(container: LinearLayout, vararg buttons: Button, stacked: Boolean = buttons.size > 2) {
        container.addView(buttonRow(*buttons, stacked = stacked), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
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
        container.addView(input, LinearLayout.LayoutParams(-1, dp(INPUT_HEIGHT_DP)).apply { topMargin = dp(16) })
        dialogActions(container,
            button(string(R.string.button_cancel)) { activity.modals.dismiss(dialog, container) },
            button(string(android.R.string.ok), filled = true) {
                val raw = input.text.toString()
                val value = raw.toIntOrNull()
                if (raw.isNotBlank() && (value == null || value !in min..max)) input.error = "$min..$max"
                else activity.modals.dismiss(dialog, container) { chosen(value) }
            })
    }

    fun dialog(title: String, message: String? = null, anchor: NativeRow? = null, onDismissed: () -> Unit = {},
               build: (Dialog, LinearLayout) -> Unit) {
        val dialog = Dialog(activity)
        val container = activity.modals.createContainer()
        val sourceTitle = anchor?.titleView?.text?.toString()
        // Heading block (title, source subtitle, message) sits tight; every body block below opens with a 16dp gap.
        container.addView(text(sourceTitle ?: title, 20f, bold = true))
        if (sourceTitle != null && sourceTitle != title) container.addView(text(title, 14f, secondary = true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        if (!message.isNullOrBlank()) container.addView(text(message, 14f, secondary = true).apply {
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
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
        dialogActions(container,
            button(string(R.string.restart_system_ui), filled = true) {
                execute("pkill -f com.android.systemui && pkill -f com.SplashScreenAdvanced.xposedmodule")
            },
            button(string(R.string.reboot)) { execute("reboot") },
            button(string(R.string.button_cancel)) { activity.modals.dismiss(dialog, container) })
    }
}

/**
 * Vertical column that, once a [NativePageUi.section] adopts it into a group card, draws a hairline above every
 * visible child but the first. Lines follow each child's translation, so they ride along with the accordion's folding
 * rows and sliding siblings instead of jumping when the layout settles. A collapsed (zero-height) child gets no line.
 */
internal open class DividedColumn(context: android.content.Context) : LinearLayout(context) {
    var drawsDividers = false
        set(value) { if (field != value) { field = value; invalidate() } }
    var dividerInsetStart = 0
    var dividerInsetEnd = 0
    var dividerColor = 0
    private val paint = android.graphics.Paint()
    /** A hairline: about 0.6dp, never thinner than one pixel. */
    private val thickness = maxOf(1f, kotlin.math.round(resources.displayMetrics.density * DIVIDER_DP))
    private var drawnLayout = 0

    /**
     * The hairlines live in this column's display list, but the accordion and the long-press elasticity move rows with
     * translationY, which only touches the row's own render node and never redraws this column: the lines would stay
     * where the rows were mid-animation. Before each frame, redraw when a row moved, faded or changed visibility.
     */
    private val redrawOnRowMove = android.view.ViewTreeObserver.OnPreDrawListener {
        if (drawsDividers && rowLayout() != drawnLayout) invalidate()
        true
    }

    private fun rowLayout(): Int {
        var hash = childCount
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            hash = 31 * hash + child.top
            hash = 31 * hash + child.translationY.toBits()
            hash = 31 * hash + child.alpha.toBits()
            hash = 31 * hash + child.visibility
        }
        return hash
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(redrawOnRowMove)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(redrawOnRowMove)
        super.onDetachedFromWindow()
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        if (!drawsDividers) return
        drawnLayout = rowLayout()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val left = (if (rtl) dividerInsetEnd else dividerInsetStart).toFloat()
        val right = width - (if (rtl) dividerInsetStart else dividerInsetEnd).toFloat()
        paint.color = dividerColor
        var first = true
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != VISIBLE || child.height == 0) continue
            if (first) { first = false; continue }
            val y = child.top + child.translationY
            // A row fading in with the accordion brings its line with it.
            paint.alpha = (android.graphics.Color.alpha(dividerColor) * child.alpha.coerceIn(0f, 1f)).toInt()
            canvas.drawRect(left, y - thickness / 2f, right, y + thickness / 2f, paint)
        }
    }

    private companion object { const val DIVIDER_DP = .6f }
}

/** Wrapper of a collapsible group of dependent rows; see [NativePageUi.conditional]. */
internal class AccordionCard(context: android.content.Context) : DividedColumn(context)

/** Gap between two stand-alone cards (status, routes, list rows). Rows inside a group card have no gap. */
internal const val ROW_GAP_DP = 8
/** Corner of a group card; a little rounder than a single row (16dp) because it is taller. */
private const val GROUP_RADIUS_DP = 20f
/** Hairline between rows of a group: ~16% of the secondary text colour, visible but never louder than the text. */
private const val DIVIDER_ALPHA = 0x29
/** Height of every action button and of single-line inputs. */
internal const val BUTTON_HEIGHT_DP = 48
internal const val INPUT_HEIGHT_DP = 52
/** Row card: 64dp tall with a one-line title, so a page of switches reads as a list rather than a stack of slabs. */
private const val ROW_MIN_HEIGHT_DP = 64

open class NativeRow(activity: NativeSettingsActivity, title: String, summary: String?) : LinearLayout(activity) {
    val titleView = TextView(activity).apply {
        text = title; textSize = 16f; setTextColor(activity.lumen.palette.textPrimary)
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    }
    val summaryView = TextView(activity).apply {
        text = summary; textSize = 13f; setTextColor(activity.lumen.palette.textSecondary)
        visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        setPadding(0, activity.dp(3), 0, 0)
        setLineSpacing(activity.dp(2).toFloat(), 1f)
    }
    val valueView = TextView(activity).apply {
        textSize = 13f; setTextColor(activity.lumen.palette.textSecondary)
        maxWidth = activity.dp(128); gravity = Gravity.END
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(activity.dp(12), 0, 0, 0)
    }
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(ROW_MIN_HEIGHT_DP)
        setPaddingRelative(activity.dp(18), activity.dp(14), activity.dp(16), activity.dp(14))
        addView(LinearLayout(activity).apply { orientation = VERTICAL; addView(titleView); addView(summaryView) },
            LayoutParams(0, -2, 1f))
        addView(valueView, LayoutParams(-2, -2))
    }
}
