package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemorySessionStore
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.state.GlobalUIViewModel
import com.SplashScreenAdvanced.xposedmodule.state.NativeUiConfig
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import com.SplashScreenAdvanced.xposedmodule.ui.page.*
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.*
import com.SplashScreenAdvanced.xposedmodule.ui.component.buildColorPickerPage
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsSearch
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsSearchItem
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsTab
import com.SplashScreenAdvanced.xposedmodule.utils.PerfTrace
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.BackupUtils
import com.SplashScreenAdvanced.xposedmodule.ui.meguviz.MeguvizFamilyBridge
import com.lumen.coacervation.engine.background.LiquidBackgroundConfig
import com.lumen.coacervation.engine.background.LiquidBackgroundImportResult
import com.lumen.coacervation.engine.background.LiquidBackgroundStore
import com.lumen.coacervation.engine.host.LumenActivityDelegate
import com.lumen.coacervation.engine.host.LumenControlShadow
import com.lumen.coacervation.engine.host.LumenControlShadowOptions
import android.graphics.drawable.Drawable
import com.lumen.coacervation.engine.interaction.LumenElasticInteraction
import com.lumen.coacervation.engine.model.LumenPalette
import com.lumen.coacervation.engine.motion.modal.LumenModalPresenter
import com.lumen.coacervation.engine.motion.morph.ContainerMorphController
import com.lumen.coacervation.engine.motion.morph.ContainerMorphHost
import com.lumen.coacervation.engine.motion.morph.ContainerMorphLauncher
import com.lumen.coacervation.engine.motion.morph.ContainerMorphOrigin
import com.lumen.coacervation.engine.motion.pager.LumenPagePager
import com.lumen.coacervation.engine.motion.pager.PageTextChain
import com.lumen.coacervation.engine.motion.reveal.LumenReveal
import com.lumen.coacervation.engine.glow.GlowBackdropTarget
import com.lumen.coacervation.engine.glow.GlowFloatingChrome
import com.lumen.coacervation.engine.glow.GlowLegibilityPolicy
import com.lumen.coacervation.engine.glow.GlowScrollEdge
import com.lumen.coacervation.engine.glow.GlowScrollEdgePolicy
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import com.lumen.coacervation.engine.widget.LumenMaxHeightScrollView
import com.lumen.coacervation.engine.widget.LumenNavigationBar
import com.lumen.coacervation.engine.widget.NavigationBarColors
import com.lumen.coacervation.engine.widget.NavigationBarSurface
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jichuo1.meguviz.android.extensions.isUiInNightMode
import org.koin.java.KoinJavaComponent

private val SYSTEM_INSETS = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

// Geometry follows BIL's floating chrome: a 58dp capsule (48dp buttons + 5dp rings, 29dp ends) 4dp below the status bar,
// and a dock 16dp above the bottom that hugs its items, keeping at least 32dp from each side (at most 480dp wide).
private const val CAPSULE_HEIGHT_DP = 58
private const val CAPSULE_RADIUS_DP = 29f
private const val CAPSULE_SIDE_MARGIN_DP = 10
private const val CAPSULE_TOP_MARGIN_DP = 4
/** Content starts below the capsule (4dp + 58dp) plus an 8dp gap and passes beneath it. */
private const val CONTENT_CLEARANCE_DP = CAPSULE_TOP_MARGIN_DP + CAPSULE_HEIGHT_DP + 8
private const val DOCK_SIDE_MARGIN_DP = 32
private const val DOCK_BOTTOM_MARGIN_DP = 16
private const val DOCK_MAX_WIDTH_DP = 480
/**
 * The dock hugs its items instead of spanning the screen: each slot is as wide as its longest label plus breathing room,
 * at least [DOCK_SLOT_MIN_DP] so the 26dp icons and the selection lens keep their proportions.
 */
private const val DOCK_SLOT_MIN_DP = 60
private const val DOCK_LABEL_ROOM_DP = 16
/** Label size of the engine's navigation bar items and its inner inset (NavigationBarMotion.INSET_DP). */
private const val DOCK_LABEL_SP = 12f
private const val DOCK_INSET_DP = 4
/** Name the engine reports for its zero-resource glass backend, the one it falls back to under memory pressure. */
private const val TRANSLUCENT_BACKEND = "TRANSLUCENT"
/** Pause before the dock pages that are not on screen start building (after the enter motion and the first frames). */
private const val PREWARM_DELAY_MS = 400L
/** Until the dock is measured: its default height, so the first frame already leaves room. */
private const val DOCK_FALLBACK_HEIGHT_DP = 68

/** Each settings page is a native Activity; the engine owns the title and container transition. */
abstract class NativeSettingsActivity : Activity() {
    abstract val pageRoute: Route
    val repository: GlobalPreferencesRepository by lazy { KoinJavaComponent.get(GlobalPreferencesRepository::class.java) }
    val uiState: GlobalUIViewModel by lazy { KoinJavaComponent.get(GlobalUIViewModel::class.java) }
    val meguviz = MeguvizFamilyBridge(this)
    val lumen = LumenActivityDelegate(this, ::resolvePalette)
    val elastic by lazy { LumenElasticInteraction(this, lumen) }
    val modals by lazy { LumenModalPresenter(this, lumen, elastic = elastic) }
    val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedJob: Job? = null
    private var morph: ContainerMorphController? = null
    private var morphHost: ContainerMorphHost? = null
    private var toolbarTitle: TextView? = null
    private lateinit var renderingConfig: NativeUiConfig
    private lateinit var renderingSkin: SkinId
    private lateinit var renderingBackground: LiquidBackgroundConfig
    private var homePager: LumenPagePager? = null
    private val headings = ArrayList<View>(SettingsTab.entries.size)
    private var textChain: PageTextChain? = null
    private var homeDock: LumenNavigationBar? = null
    private var contentTarget: GlowBackdropTarget? = null
    private var topCapsule: LinearLayout? = null
    private val stretches = arrayOfNulls<View>(SettingsTab.entries.size)
    private var detailStretch: View? = null
    private var previousPage = 0
    private var chromeReady = false
    private var chrome: GlowFloatingChrome? = null
    /** The scrolling page of a detail route, when it has one; list pages keep fixed controls below the capsule. */
    private var detailScroll: NestedScrollView? = null
    private val contentScrolls = mutableListOf<NestedScrollView>()
    private var insetLeft = 0
    private var insetRight = 0
    private var insetTop = 0
    private var insetBottom = 0
    private var topInsetPx = 0f
    private var bottomInsetPx = 0f
    private var dockHeightPx = 0
    private var topButtons: List<View> = emptyList()
    private val homePageHosts = mutableListOf<FrameLayout>()
    private var touching = false
    private var prewarmQueued = false
    lateinit var pageUi: NativePageUi
        private set
    protected var restoredPageState: Bundle? = null
        private set
    private val backCallback = object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) { morph?.beginPredictiveBack() }
        override fun onBackProgressed(backEvent: BackEvent) { morph?.progressPredictiveBack(backEvent.progress) }
        override fun onBackCancelled() { morph?.cancelPredictiveBack() }
        override fun onBackInvoked() { requestBack() }
    }
    private var backCallbackRegistered = false

    /**
     * Registers [backCallback] only while back has something to do inside the app. On the home page at its first tab
     * back leaves the app, and any registered callback would hide the system's predictive back-to-home preview.
     */
    private fun syncBackCallback() {
        if (isDestroyed) return
        val needed = pageRoute != Route.Main || (homePager?.selectedPage ?: 0) != 0 || pageUi.hasUnsavedChanges
        if (needed == backCallbackRegistered) return
        if (needed) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        else onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        backCallbackRegistered = needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredPageState = savedInstanceState?.getBundle("native.page")
        renderingConfig = repository.uiConfigFlow.value
        renderingSkin = LumenEngine.requestedMaterial(this)
        renderingBackground = LiquidBackgroundStore.read(this).config
        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        ContainerMorphController.suppressSystemTransitions(this)
        PerfTrace.section("act.lumen.prepare") { lumen.prepare() }
        pageUi = NativePageUi(this, restoredPageState)
        val page = PerfTrace.section("act.buildPage") { buildPage() }
        if (pageRoute == Route.Main) {
            PerfTrace.section("act.meguviz.install") { meguviz.install(page) }
            page.requestApplyInsets()
            lumen.bindRoot(page) { if (!isFinishing && !isDestroyed) recreate() }
            PerfTrace.section("act.setupChrome") { setupChrome() }
        } else {
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            val host = ContainerMorphHost(this, lumen.palette.surface, lumen.palette.background,
                lumen.palette.textPrimary, toolbarTitle?.text?.toString().orEmpty())
            morphHost = host
            // Engine surfaces go straight onto their View: they locate the host through `callback as? View` to sample.
            // The expanded page meets the screen's own rounded corners instead of square edges (API 31+, engine §13.5).
            host.followScreenCorners = true
            host.setMotionSurfaceBackground(lumen.motionSurfaceBackground(lumen.palette.surface, 16f))
            host.replacePage(page, checkNotNull(toolbarTitle))
            // The page's chrome owns status-bar insets; padding the host would leave a material seam.
            host.requestApplyInsets()
            pageUi.backButton?.let(host::registerNavigationBack)
            meguviz.install(host)
            lumen.bindRoot(host.liquidBackdropRoot()) { if (!isFinishing && !isDestroyed) recreate() }
            setupChrome()
            morph = ContainerMorphController(this, host, lumen, javaClass,
                ContainerMorphOrigin.from(intent), savedInstanceState == null, { toolbarTitle },
                collapsedCornerRadiusDp = 16f, isBusinessBlocked = { pageUi.hasUnsavedChanges })
            morph?.start(isFreshLaunch = savedInstanceState == null)
        }
        syncBackCallback()
        window.insetsController?.setSystemBarsAppearance(
            if (isDark()) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }

    /**
     * Engine layout (integration standard §6.0): content sits in a [GlowBackdropTarget]; the top capsule and the
     * dock are its siblings so the glass samples what scrolls beneath them.
     */
    private fun buildPage(): View {
        val page = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val target = GlowBackdropTarget(this)
        contentTarget = target
        val body = when (pageRoute) {
            Route.Main -> buildHomeBody()
            Route.Basic -> pageUi.buildBasicPage()
            Route.Scope -> pageUi.buildScopePage()
            Route.Icon -> pageUi.buildIconPage()
            Route.Bottom -> pageUi.buildBottomPage()
            Route.Background -> pageUi.buildBackgroundPage()
            Route.Display -> pageUi.buildDisplayPage()
            Route.Developer -> pageUi.buildDevPage()
            Route.About -> pageUi.buildAboutPage()
            Route.CustomScope -> pageUi.buildCustomScopePage()
            Route.IgnoreAppIcon -> pageUi.buildIgnoreAppIconPage()
            Route.HideIcon -> pageUi.buildHideIconPage()
            Route.RemoveBranding -> pageUi.buildRemoveBrandingPage()
            Route.BackgroundExcept -> pageUi.buildBackgroundExceptPage()
            Route.ForceSplash -> pageUi.buildForceSplashPage()
            Route.MinDuration -> pageUi.buildMinDurationPage()
            Route.BgIndividual -> pageUi.buildBgIndividualPage()
            is Route.ColorPicker -> pageUi.buildColorPickerPage((pageRoute as Route.ColorPicker).pkgName)
            Route.Empty -> pageUi.scrollContent { addView(pageUi.text(getString(R.string.app_name))) }
        }
        detailScroll = if (pageRoute == Route.Main) null else body as? NestedScrollView
        detailScroll?.let { contentScrolls += it }
        val viewport = NativeContentViewport(this).apply {
            id = R.id.native_content_viewport
            addView(body, FrameLayout.LayoutParams(-1, -1))
        }
        target.addView(viewport, FrameLayout.LayoutParams(-1, -1))
        page.addView(target, FrameLayout.LayoutParams(-1, -1))
        val capsule = buildTopCapsule()
        topCapsule = capsule
        page.addView(capsule, FrameLayout.LayoutParams(-1, dp(CAPSULE_HEIGHT_DP), Gravity.TOP).apply {
            setMargins(dp(CAPSULE_SIDE_MARGIN_DP), dp(CAPSULE_TOP_MARGIN_DP), dp(CAPSULE_SIDE_MARGIN_DP), 0)
        })
        homeDock?.let {
            page.addView(it, FrameLayout.LayoutParams(minOf(dp(DOCK_MAX_WIDTH_DP), dockNaturalWidth), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(0, 0, 0, dp(DOCK_BOTTOM_MARGIN_DP))
            })
            it.addOnLayoutChangeListener { dock, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) {
                    dockHeightPx = dock.height
                    onDockResized()
                }
            }
        }
        val split = splitLayout
        page.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> updateDockWidth(view.width) }
        meguviz.claimInsets(page, SYSTEM_INSETS) { safe ->
            val left = if (!split || pageRoute == Route.Main) safe.left else 0
            val right = if (!split || pageRoute != Route.Main) safe.right else 0
            insetLeft = left; insetRight = right; insetTop = safe.top; insetBottom = safe.bottom
            (capsule.layoutParams as FrameLayout.LayoutParams).let {
                if (it.leftMargin != dp(CAPSULE_SIDE_MARGIN_DP) + left || it.rightMargin != dp(CAPSULE_SIDE_MARGIN_DP) + right ||
                    it.topMargin != safe.top + dp(CAPSULE_TOP_MARGIN_DP)) {
                    it.setMargins(dp(CAPSULE_SIDE_MARGIN_DP) + left, safe.top + dp(CAPSULE_TOP_MARGIN_DP),
                        dp(CAPSULE_SIDE_MARGIN_DP) + right, 0)
                    capsule.layoutParams = it
                }
            }
            homeDock?.let { dock ->
                (dock.layoutParams as FrameLayout.LayoutParams).let {
                    if (it.bottomMargin != safe.bottom + dp(DOCK_BOTTOM_MARGIN_DP)) {
                        it.bottomMargin = safe.bottom + dp(DOCK_BOTTOM_MARGIN_DP); dock.layoutParams = it
                    }
                }
            }
            // Scrolling pages carry their own padding so rows pass beneath the bars; fixed-layout pages stay below the capsule.
            val underBars = pageRoute == Route.Main || detailScroll != null
            viewport.setPadding(left, if (underBars) 0 else safe.top + dp(CONTENT_CLEARANCE_DP), right,
                if (underBars) 0 else safe.bottom)
            topInsetPx = (safe.top + dp(CONTENT_CLEARANCE_DP)).toFloat()
            onDockResized()
            updateDockWidth(page.width)
            chrome?.onContentMoved()
        }
        if (!split) return page
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            if (pageRoute == Route.Main) {
                addView(page, LinearLayout.LayoutParams(0, -1, 1f))
                addView(android.widget.ImageView(this@NativeSettingsActivity).apply {
                    setImageResource(R.drawable.ic_launcher_foreground)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(48), dp(48), dp(48), dp(48))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(0, -1, 2f))
            } else {
                addView(NativeContentViewport(this@NativeSettingsActivity).apply {
                    addView(pageUi.buildMainPage("masterScroll"), FrameLayout.LayoutParams(-1, -1))
                    meguviz.claimInsets(this, SYSTEM_INSETS) { safe -> setPadding(safe.left, safe.top, 0, safe.bottom) }
                }, LinearLayout.LayoutParams(0, -1, 1f))
                addView(page, LinearLayout.LayoutParams(0, -1, 2f))
            }
            clipChildren = false; clipToPadding = false
        }
    }

    /** Adaptive two-pane layout: wide landscape or tablet screens with the setting on. */
    private val splitLayout: Boolean
        get() {
            val configuration = resources.configuration
            return renderingConfig.isSplitScreenEnabled &&
                (configuration.screenWidthDp > configuration.screenHeightDp ||
                    configuration.screenWidthDp >= 840 && configuration.screenHeightDp >= 480)
        }

    /** The dock pages. The dock itself is a sibling of the content container, added by [buildPage]. */
    private fun buildHomeBody(): View {
        val pager = LumenPagePager(this)
        val palette = lumen.palette
        val dock = LumenNavigationBar(
            context = this,
            titles = SettingsTab.entries.map { getString(it.labelRes) },
            icons = SettingsTab.entries.map { it.iconRes }.toIntArray(),
            colors = NavigationBarColors(text = palette.textPrimary, selectedText = palette.primary, highlight = palette.primary),
            // BIL's recipe: the bar is a floating surface and the selection a chrome lens, both in the surface colour,
            // plus the engine's soft shadow around the bar. Not wrapped otherwise: the drawables must see the dock View
            // as their callback to sample the backdrop.
            backgroundFactory = { surface, radius ->
                when (surface) {
                    NavigationBarSurface.BAR -> withBarShadow(lumen.floatingBackground(palette.surface, radius), radius)
                    NavigationBarSurface.SELECTION -> lumen.chromeOverlayBackground(palette.surface, radius, selected = true)
                }
            },
            onSelect = { pager.selectPage(it) },
            onUserInteraction = {},
            onVisualMovement = { lumen.notifyPositionChanged() },
        ).apply {
            id = R.id.native_bottom_navigation
            // The dock handles its own press, drag and glow; it stays out of the global long-press elasticity.
            tag = ElasticInteractionController.EXCLUDED_TAG
        }
        homePager = pager
        homeDock = dock
        // Texts follow the finger or the dock tap in a wave; the page headings lag behind (standard §13.6).
        textChain = PageTextChain(pager, headings)
        SettingsTab.entries.forEach {
            homePageHosts += FrameLayout(this).also { host -> pager.addView(host, FrameLayout.LayoutParams(-1, -1)) }
        }
        pager.onPositionChanged = {
            ensureHomePage(pager.pagePosition.toInt().coerceIn(0, SettingsTab.entries.lastIndex))
            ensureHomePage(kotlin.math.ceil(pager.pagePosition.toDouble()).toInt().coerceIn(0, SettingsTab.entries.lastIndex))
            dock.setPageProgress(pager.pagePosition, notifyPositionChanged = false)
            lumen.notifyPositionChanged()
            chrome?.onContentMoved()
            textChain?.onPositionChanged()
        }
        pager.onPageSelected = { index ->
            ensureHomePage(index)
            pageUi.shownTab.value = SettingsTab.entries[index]
            stretches.getOrNull(previousPage)?.let(lumen::finishStretch)
            previousPage = index
            dock.setSelectedPage(index)
            syncBackCallback()
            updatePageTitle(getString(SettingsTab.entries[index].titleRes))
        }
        pager.onMotionStarted = { stretches.forEach(lumen::finishStretch) }
        val restored = restoredPageState?.getInt("native.home.tab")?.coerceIn(0, SettingsTab.entries.lastIndex) ?: 0
        ensureHomePage(restored)
        pageUi.shownTab.value = SettingsTab.entries[restored]
        pager.selectPage(restored, false)
        previousPage = restored
        dock.setSelectedPage(restored)
        dock.setPageProgress(pager.pagePosition, notifyPositionChanged = false)
        return pager
    }

    /**
     * Floating capsule on top, built like BIL's: the capsule carries the engine's floating surface, and each button is
     * its own round glass surface so long-press elasticity deforms them one by one.
     */
    /**
     * Soft shadow of the engine for the floating bars (standard §5.6): on a light theme a light bar over white content
     * needs it to stand off the page. It is drawn outside the bar bounds; the parent (page root) keeps
     * `clipChildren = false`. The shadow drawable hands its own View callback on to the engine background at draw time,
     * so it is the only wrapper allowed around a sampling surface.
     */
    private val barShadow = LumenControlShadowOptions(enabled = true)

    private fun withBarShadow(content: Drawable, radiusDp: Float): Drawable =
        LumenControlShadow.wrap(content, radiusDp, resources.displayMetrics.density, barShadow, isDark())

    private fun buildTopCapsule(): LinearLayout {
        val isMain = pageRoute == Route.Main
        val buttons = mutableListOf<View>()
        val capsule = LinearLayout(this).apply {
            id = R.id.native_chrome
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = withBarShadow(lumen.floatingBackground(lumen.palette.surface, CAPSULE_RADIUS_DP), CAPSULE_RADIUS_DP)
            // The capsule itself is pressable so a long press off its buttons deforms the whole bar.
            isClickable = true
            clipChildren = false
            clipToPadding = false
            fun button(view: ImageView, start: Int = 0, end: Int = 0) {
                buttons += view
                addView(view, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginStart = dp(start); marginEnd = dp(end)
                })
            }
            if (!isMain) {
                val back = pageUi.iconButton(R.drawable.ic_back, getString(R.string.native_back)) { requestBack() }
                pageUi.backButton = back
                button(back, start = 5)
            }
            // The home body restores its dock page before the capsule exists, so the title starts from the restored tab.
            val restoredTab = restoredPageState?.getInt("native.home.tab")?.coerceIn(0, SettingsTab.entries.lastIndex) ?: 0
            val initialTitle = intent.getStringExtra("native.title")
                ?: getString(if (isMain) SettingsTab.entries[restoredTab].titleRes else pageTitleResource(pageRoute))
            val title = pageUi.text(initialTitle, 20f, bold = true).apply {
                id = R.id.native_title
                setPadding(dp(if (isMain) 20 else 12), 0, dp(8), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            toolbarTitle = title
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            if (isMain) {
                button(pageUi.iconButton(R.drawable.ic_search, getString(R.string.settings_search)) { showSettingsSearch() })
                button(pageUi.iconButton(R.drawable.ic_restart, getString(R.string.restart_title)) { pageUi.showRestartDialog() },
                    start = 4, end = 5)
            }
        }
        topButtons = buttons
        return capsule
    }

    /** Room the dock takes at the bottom: its own height plus its 16dp margin and a 16dp gap (BIL's dock inset). */
    private fun dockClearancePx(): Int =
        (if (dockHeightPx > 0) dockHeightPx else dp(DOCK_FALLBACK_HEIGHT_DP)) + dp(DOCK_BOTTOM_MARGIN_DP * 2)

    /** Scrolling content passes beneath the capsule and the dock: padding, not the viewport, keeps it clear when at rest. */
    private fun applyScrollPadding(scroll: NestedScrollView) {
        scroll.setPadding(0, insetTop + dp(CONTENT_CLEARANCE_DP), 0,
            insetBottom + if (homeDock != null) dockClearancePx() else 0)
    }

    private fun onDockResized() {
        bottomInsetPx = (insetBottom + dockClearancePx()).toFloat()
        contentScrolls.forEach(::applyScrollPadding)
        chrome?.onContentMoved()
    }

    private fun updateDockWidth(rootWidth: Int) {
        val dock = homeDock ?: return
        val params = dock.layoutParams as? FrameLayout.LayoutParams ?: return
        // The side margins always win: the old 72dp-per-tab floor pushed a five-tab dock past them on a 393dp phone.
        val available = (rootWidth - dp(DOCK_SIDE_MARGIN_DP * 2) - insetLeft - insetRight).coerceAtLeast(0)
        val width = minOf(dp(DOCK_MAX_WIDTH_DP), available, dockNaturalWidth)
        if (rootWidth > 0 && params.width != width) { params.width = width; dock.layoutParams = params }
    }

    /** Width the dock needs for its labels: equal slots sized by the longest label, plus the bar's own inset. */
    private val dockNaturalWidth: Int by lazy(LazyThreadSafetyMode.NONE) {
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, DOCK_LABEL_SP,
                resources.displayMetrics)
        }
        val widestLabel = SettingsTab.entries.maxOf { paint.measureText(getString(it.labelRes)) }
        val slot = maxOf(dp(DOCK_SLOT_MIN_DP), kotlin.math.ceil(widestLabel).toInt() + dp(DOCK_LABEL_ROOM_DP))
        slot * SettingsTab.entries.size + dp(DOCK_INSET_DP * 2)
    }

    /** Engine rubber band for a dock page: only the current page, and only once the pager has settled (standard §5.4). */
    private fun installPageStretch(index: Int) {
        if (!chromeReady || stretches[index] != null) return
        val scroll = homePageHosts[index].getChildAt(0) as? NestedScrollView ?: return
        stretches[index] = lumen.installStretch(scroll) { homePager?.let { it.selectedPage == index && it.isSettled } == true }
    }

    /** Registers the capsule and the dock with the engine's floating-chrome coordinator (edge dissolve, legibility, glass). */
    private fun setupChrome() {
        val target = contentTarget ?: return
        val capsule = topCapsule ?: return
        val palette = lumen.palette
        chromeReady = true
        homePageHosts.indices.forEach(::installPageStretch)
        detailScroll?.let { scroll -> detailStretch = lumen.installStretch(scroll) { morph?.isSettledExpanded == true } }
        // The content layer under the floating surfaces; never an ancestor of them (standard §6.1).
        lumen.bindContentSource(homePager ?: target)
        chrome = GlowFloatingChrome(target, { lumen.engine }, ::edgeCoverage).apply {
            // As BIL: only the round buttons thicken for legibility; the capsule body stays as clear as the dock.
            // Thickening the body over bright content capped the boost and washed the whole capsule grey. The title on
            // the body is kept readable by pushing its colour instead (onForeground).
            attach(capsule, GlowScrollEdge.TOP, palette.textPrimary, companions = topButtons, thickenHost = false,
                onForeground = foregroundFor(capsule, palette))
            homeDock?.let { dock ->
                attach(dock, GlowScrollEdge.BOTTOM, palette.textPrimary) { boost -> dock.setLegibility(boost, palette.surface) }
            }
        }
    }

    private fun edgeCoverage(edge: GlowScrollEdge): Float {
        val pager = homePager
        if (pager != null) {
            return GlowScrollEdgePolicy.pagerCoverage(pager.pagePosition, SettingsTab.entries.size) { index ->
                scrollCoverage(homePageHosts[index].getChildAt(0) as? NestedScrollView, edge)
            }
        }
        return scrollCoverage(detailScroll, edge)
    }

    private fun scrollCoverage(scroll: NestedScrollView?, edge: GlowScrollEdge): Float {
        scroll ?: return 0f
        return when (edge) {
            GlowScrollEdge.TOP -> GlowScrollEdgePolicy.topCoverage(scroll.scrollY, topInsetPx)
            GlowScrollEdge.BOTTOM -> {
                val child = scroll.getChildAt(0)
                val range = if (child == null) 0 else
                    (child.height - (scroll.height - scroll.paddingTop - scroll.paddingBottom)).coerceAtLeast(0)
                GlowScrollEdgePolicy.bottomCoverage(scroll.scrollY, range, bottomInsetPx)
            }
        }
    }

    /** Legibility compensation: 0 keeps the original colour, 1 is the strongest same-direction push. */
    private fun foregroundFor(bar: ViewGroup, palette: LumenPalette): (Float) -> Unit = { boost ->
        val text = GlowLegibilityPolicy.foreground(palette.textPrimary, boost)
        val icon = ColorStateList.valueOf(GlowLegibilityPolicy.foreground(palette.textPrimary, boost))
        for (i in 0 until bar.childCount) {
            when (val child = bar.getChildAt(i)) {
                is TextView -> child.setTextColor(text)
                is ImageView -> child.imageTintList = icon
            }
        }
    }

    private fun ensureHomePage(index: Int) {
        val host = homePageHosts[index]
        if (host.childCount > 0) return
        val tab = SettingsTab.entries[index]
        pageUi.stateNamespace = "home.${tab.name}."
        val content = PerfTrace.section("page.build.${tab.name}") { when (tab) {
            SettingsTab.Home -> pageUi.buildHomePage(withRoutes = splitLayout)
            SettingsTab.Icon -> pageUi.buildIconPage()
            SettingsTab.Background -> pageUi.buildBackgroundPage()
            SettingsTab.Display -> pageUi.buildDisplayPage(withBranding = true)
            SettingsTab.Module -> pageUi.buildModulePage()
        } }
        (content as? NestedScrollView)?.let { contentScrolls += it; applyScrollPadding(it) }
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        PerfTrace.section("page.adopt.${tab.name}") { meguviz.adopt(content, "home/${tab.name}") }
        PerfTrace.section("page.stretch.${tab.name}") { installPageStretch(index) }
        (content as? NestedScrollView)?.let(::pageHeading)?.let { headings += it }
    }

    /**
     * Builds the dock pages that are not on screen yet, one per idle frame and nearest to the current page first, so a swipe
     * or the search never builds a page on the main thread while the user is moving. The frames come from the window's
     * Meguviz frame scheduler, which only runs them while the window is started; a touch or a moving pager defers the queue.
     */
    private fun prewarmHomePages() {
        if (isFinishing || isDestroyed) return
        val current = homePager?.selectedPage ?: return
        SettingsTab.entries.indices.filter { homePageHosts[it].childCount == 0 }
            .sortedBy { kotlin.math.abs(it - current) }
            .forEach { index -> meguviz.runWhenIdle(ready = ::isIdleForPrewarm) { ensureHomePage(index) } }
    }

    private fun isIdleForPrewarm() = !touching && !isFinishing && homePager?.isSettled == true

    /** The first text on a page (its header card title, or the status title on Home) is the heading of the text chain. */
    private fun pageHeading(scroll: NestedScrollView): TextView? {
        fun firstText(view: View?): TextView? = when (view) {
            is TextView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { firstText(view.getChildAt(it)) }
            else -> null
        }
        return firstText((scroll.getChildAt(0) as? ViewGroup)?.getChildAt(0))
    }

    /** Searches every row on every dock page, so each page is built first (usually done already by [prewarmHomePages]): rows register as they are built. */
    private fun showSettingsSearch() {
        PerfTrace.section("search.buildAllPages") { SettingsTab.entries.indices.forEach(::ensureHomePage) }
        val targets = pageUi.searchTargets.associateBy { it.titleRes }
        val items = targets.values.map {
            SettingsSearchItem(it.titleRes, getString(it.titleRes), getString(it.section.titleRes),
                getString(it.section.tab.labelRes), SettingsSearch.order(it.section, it.sequence))
        }
        pageUi.dialog(getString(R.string.settings_search)) { dialog, container ->
            val results = pageUi.column().apply { id = R.id.native_settings_search_results }
            val input = pageUi.engineEditText(EditText(this).apply {
                id = R.id.native_settings_search
                hint = getString(R.string.settings_search_hint)
                setTextColor(lumen.palette.textPrimary)
                setHintTextColor(lumen.palette.textSecondary)
                isSingleLine = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
            })
            fun render(query: String) {
                results.removeAllViews()
                if (query.isBlank()) {
                    results.addView(pageUi.text(getString(R.string.settings_search_hint), 14f, secondary = true))
                    return
                }
                val matches = SettingsSearch.rank(query, items)
                if (matches.isEmpty()) {
                    results.addView(pageUi.text(getString(R.string.settings_search_empty), 14f, secondary = true))
                    return
                }
                matches.forEach { match ->
                    val place = getString(R.string.settings_search_location, match.tab, match.section)
                    results.addView(pageUi.action(match.title, place, moduleRequired = false) {
                        modals.dismiss(dialog, container) { targets[match.key]?.let(::revealSetting) }
                    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ROW_GAP_DP) })
                }
            }
            input.doAfterTextChanged { render(it?.toString().orEmpty()) }
            container.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
            container.addView(LumenMaxHeightScrollView(this, minOf(dp(320), resources.displayMetrics.heightPixels / 2))
                .apply { addView(results) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            render("")
            input.requestFocus()
        }
    }

    /** Locate-and-highlight of the engine (standard §13.8): scroll to a docking point below the capsule, then flash the row. */
    private val reveal by lazy(LazyThreadSafetyMode.NONE) { LumenReveal(lumen.palette.primary) }

    fun cancelReveal() = reveal.cancel()

    /** Opens the row's dock page and lets the engine bring it into view and highlight it. */
    private fun revealSetting(target: NativePageUi.SettingsTarget) {
        val index = target.section.tab.ordinal
        val pager = homePager ?: return
        ensureHomePage(index)
        pager.selectPage(index)
        val scroll = homePageHosts[index].getChildAt(0) as? NestedScrollView ?: return
        reveal.reveal(scroll, revealAnchor(target.view), topOffsetPx = insetTop + dp(CONTENT_CLEARANCE_DP))
    }

    /** A dependent row collapsed under its switch cannot be shown; the nearest visible row above the collapsed group stands in. */
    private fun revealAnchor(view: View): View {
        var collapsed: View? = null
        var node: View? = view
        while (node != null) {
            if (node.visibility == View.GONE) collapsed = node
            node = node.parent as? View
        }
        var current: View = collapsed ?: return view
        while (true) {
            val parent = current.parent as? ViewGroup ?: return view
            for (i in parent.indexOfChild(current) - 1 downTo 0) {
                parent.getChildAt(i).takeIf { it.visibility == View.VISIBLE && it.height > 0 }?.let { return it }
            }
            current = parent
        }
    }

    fun navigate(route: Route, source: View, title: TextView) {
        if (morph?.isExpanded == false) return
        val destination = activityForRoute(route)
        ContainerMorphLauncher.launch(this, destination, source, title, configure = {
            it.putExtra("native.title", title.text.toString())
            if (route is Route.ColorPicker) it.putExtra("native.package", route.pkgName)
        })
    }

    fun requestBack() {
        if (pageUi.confirmDiscard { closePage() }) return
        val pager = homePager
        if (pageRoute == Route.Main && pager != null && pager.selectedPage != 0) {
            pager.selectPage(0)
            return
        }
        closePage()
    }

    fun updatePageTitle(title: String) { toolbarTitle?.text = title }

    private fun closePage() { morph?.commitBack() ?: finish() }
    fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    fun isDark(): Boolean = resources.configuration.isUiInNightMode

    @Suppress("DEPRECATION")
    fun launchBackup() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "SplashScreenAdvanced_${java.time.LocalDateTime.now()}.json")
        }, 401)
    }

    @Suppress("DEPRECATION")
    fun launchRestore() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
    }, 402) }

    fun launchBackgroundPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, 404)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == 401) BackupUtils.handleCreateDocument(this, data?.data)
        if (requestCode == 402) BackupUtils.handleReadDocument(this, data?.data)
        if (requestCode == 404) {
            val uri = data?.data ?: return
            uiScope.launch {
                when (withContext(Dispatchers.IO) { LiquidBackgroundStore.importFromUri(applicationContext, uri) }) {
                    is LiquidBackgroundImportResult.Success -> if (!isFinishing && !isDestroyed) recreate()
                    is LiquidBackgroundImportResult.Failure -> toast(R.string.background_import_failed)
                }
            }
        }
    }

    fun requestScanPermission() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            com.SplashScreenAdvanced.xposedmodule.utils.sr.IconScanService.start(this)
        } else requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 403)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 403) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) toast(R.string.sr_scan_notification_denied)
            com.SplashScreenAdvanced.xposedmodule.utils.sr.IconScanService.start(this)
        }
    }

    private fun resolvePalette(): LumenPalette {
        val dark = isDark()
        val dynamic = repository.get(com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences.Module.UI_STYLE) == 1
        // Accents follow the chosen style (Monet: the system's tonal palette, tone 40 light / 80 dark, as Material 3).
        fun accent(light: Int, darkTone: Int) = getColor(if (dark) darkTone else light)
        val primary = if (dynamic) accent(android.R.color.system_accent1_600, android.R.color.system_accent1_200)
            else if (dark) Color.rgb(156, 199, 255) else Color.rgb(36, 105, 206)
        val onPrimary = if (dynamic) accent(android.R.color.system_accent1_0, android.R.color.system_accent1_800)
            else if (dark) Color.rgb(0, 49, 94) else Color.WHITE
        val secondary = if (dynamic) accent(android.R.color.system_accent2_600, android.R.color.system_accent2_200) else primary
        val tertiary = if (dynamic) accent(android.R.color.system_accent3_600, android.R.color.system_accent3_200) else primary
        // Large surfaces, window and text use the engine's neutral "modern" set, the values BIL's settings run on.
        // The old blue-tinted surfaces (#ECF2FB window, #E1E8F3 variant) tinted the ambient backdrop and every glass
        // surface over it, so the bars read bluish-grey instead of clear.
        return LumenPalette.modern(primary, onPrimary, secondary, tertiary, dark)
    }

    /** Best Liquid backend this window has drawn with; see [recoverDegradedGlass]. */
    private var liquidBackendSeen: String? = null

    private fun liquidBackend(): String? =
        lumen.diagnostics()?.takeIf { it.effectiveSkin == SkinId.LIQUID }?.liquidBackendName

    /**
     * Under memory pressure (opening and closing pages on a busy device is enough) the engine drops this window's glass to
     * its translucent backend for good: only the tint and rim remain over the unblurred background, which reads as a dark
     * film inside every card. The engine heals that only at a session boundary, so a window that drew real glass before
     * and comes back to the front degraded starts a fresh session. A session that was translucent from the start keeps it.
     * Checked on resume: secondary pages are translucent windows, so the page below is only paused, never stopped.
     */
    private fun recoverDegradedGlass(): Boolean {
        val backend = liquidBackend()
        if (liquidBackendSeen != null && backend == TRANSLUCENT_BACKEND && !isFinishing) {
            recreate()
            return true
        }
        if (backend != null && backend != TRANSLUCENT_BACKEND) liquidBackendSeen = backend
        return false
    }

    override fun onStart() {
        super.onStart()
        meguviz.onStart()
        lumen.onStart()
    }

    override fun onResume() {
        super.onResume()
        meguviz.onResume()
        if (renderingConfig != repository.uiConfigFlow.value || renderingSkin != LumenEngine.requestedMaterial(this) ||
            renderingBackground != LiquidBackgroundStore.read(this).config) {
            recreate()
            return
        }
        if (recoverDegradedGlass()) return
        FairMemorySessionStore.remember(pageRoute)
        uiState.refreshRestartState()
        pageUi.refresh()
        if (pageRoute == Route.Main && !prewarmQueued) {
            prewarmQueued = true
            // After the enter motion and the first frames: the visible page comes first, the rest follow when idle.
            window.decorView.postDelayed(::prewarmHomePages, PREWARM_DELAY_MS)
        }
        startedJob?.cancel()
        startedJob = uiScope.launch {
            launch { uiState.state.collect { pageUi.refresh() } }
            launch { repository.preferenceUpdates.collect { pageUi.refresh() } }
            // A reload (service bound, backup restored, settings reset) can change the palette or the layout the window
            // was built with; the page's own refresh cannot repaint the engine palette, so the window is rebuilt instead.
            launch { repository.globalReloadEvent.collect {
                if (renderingConfig != repository.uiConfigFlow.value && !pageUi.hasUnsavedChanges) recreate()
                else pageUi.onPreferencesReloaded()
            } }
            launch { pageUi.observeUpdates() }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        lumen.onDispatchTouchEvent(event)
        return elastic.dispatch(event) { super.dispatchTouchEvent(it) }
    }

    override fun onPause() { startedJob?.cancel(); startedJob = null; reveal.cancel(); elastic.clear(); meguviz.onPause(); super.onPause() }
    override fun onStop() { startedJob?.cancel(); startedJob = null; elastic.clear(); meguviz.onStop(); lumen.onStop(); super.onStop() }
    override fun onTrimMemory(level: Int) { lumen.onTrimMemory(level); super.onTrimMemory(level) }
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() { lumen.onLowMemory(); super.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("native.page", pageUi.saveState().apply {
            homePager?.let { putInt("native.home.tab", it.selectedPage) }
        })
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (backCallbackRegistered) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        backCallbackRegistered = false
        uiScope.cancel()
        pageUi.dispose()
        morph?.onDestroy()
        modals.onDestroy()
        elastic.dispose()
        textChain?.dispose()
        stretches.forEach(lumen::finishStretch)
        lumen.finishStretch(detailStretch)
        chrome?.dispose()
        chrome = null
        homeDock?.dispose()
        lumen.onDestroy()
        meguviz.close()
        super.onDestroy()
    }
}

class BasicActivity : NativeSettingsActivity() { override val pageRoute = Route.Basic }
class ScopeActivity : NativeSettingsActivity() { override val pageRoute = Route.Scope }
class IconActivity : NativeSettingsActivity() { override val pageRoute = Route.Icon }
class BottomActivity : NativeSettingsActivity() { override val pageRoute = Route.Bottom }
class BackgroundActivity : NativeSettingsActivity() { override val pageRoute = Route.Background }
class DisplayActivity : NativeSettingsActivity() { override val pageRoute = Route.Display }
class DeveloperActivity : NativeSettingsActivity() { override val pageRoute = Route.Developer }
class AboutActivity : NativeSettingsActivity() { override val pageRoute = Route.About }
class CustomScopeActivity : NativeSettingsActivity() { override val pageRoute = Route.CustomScope }
class IgnoreAppIconActivity : NativeSettingsActivity() { override val pageRoute = Route.IgnoreAppIcon }
class HideIconActivity : NativeSettingsActivity() { override val pageRoute = Route.HideIcon }
class RemoveBrandingActivity : NativeSettingsActivity() { override val pageRoute = Route.RemoveBranding }
class BackgroundExceptActivity : NativeSettingsActivity() { override val pageRoute = Route.BackgroundExcept }
class ForceSplashActivity : NativeSettingsActivity() { override val pageRoute = Route.ForceSplash }
class MinDurationActivity : NativeSettingsActivity() { override val pageRoute = Route.MinDuration }
class BgIndividualActivity : NativeSettingsActivity() { override val pageRoute = Route.BgIndividual }
class ColorPickerActivity : NativeSettingsActivity() {
    override val pageRoute: Route get() = Route.ColorPicker(intent.getStringExtra("native.package").orEmpty())
}

fun activityForRoute(route: Route): Class<out Activity> = when (route) {
    Route.Basic -> BasicActivity::class.java
    Route.Scope -> ScopeActivity::class.java
    Route.Icon -> IconActivity::class.java
    Route.Bottom -> BottomActivity::class.java
    Route.Background -> BackgroundActivity::class.java
    Route.Display -> DisplayActivity::class.java
    Route.Developer -> DeveloperActivity::class.java
    Route.About -> AboutActivity::class.java
    Route.CustomScope -> CustomScopeActivity::class.java
    Route.IgnoreAppIcon -> IgnoreAppIconActivity::class.java
    Route.HideIcon -> HideIconActivity::class.java
    Route.RemoveBranding -> RemoveBrandingActivity::class.java
    Route.BackgroundExcept -> BackgroundExceptActivity::class.java
    Route.ForceSplash -> ForceSplashActivity::class.java
    Route.MinDuration -> MinDurationActivity::class.java
    Route.BgIndividual -> BgIndividualActivity::class.java
    is Route.ColorPicker -> ColorPickerActivity::class.java
    else -> com.SplashScreenAdvanced.xposedmodule.ui.MainActivity::class.java
}

fun pageTitleResource(route: Route): Int = when (route) {
    Route.Basic -> R.string.basic_settings
    Route.Scope -> R.string.custom_scope_settings
    Route.Icon -> R.string.icon_settings
    Route.Bottom -> R.string.bottom_settings
    Route.Background -> R.string.background_settings
    Route.Display -> R.string.display_settings
    Route.Developer -> R.string.dev_settings
    Route.About -> R.string.about
    Route.CustomScope -> R.string.custom_scope_title
    Route.IgnoreAppIcon -> R.string.default_style_title
    Route.HideIcon -> R.string.hide_splash_screen_icon_title
    Route.RemoveBranding -> R.string.background_image_title
    Route.BackgroundExcept -> R.string.background_except_title
    Route.ForceSplash -> R.string.force_show_splash_screen_title
    Route.MinDuration -> R.string.min_duration
    Route.BgIndividual -> R.string.configure_bg_colors_individually
    is Route.ColorPicker -> R.string.set_custom_bg_color
    else -> R.string.app_name
}
