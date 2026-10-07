package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
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
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.BackupUtils
import com.lumen.coacervation.engine.host.LumenActivityDelegate
import com.lumen.coacervation.engine.interaction.LumenElasticInteraction
import com.lumen.coacervation.engine.model.LumenPalette
import com.lumen.coacervation.engine.model.SurfaceRole
import com.lumen.coacervation.engine.motion.modal.LumenModalPresenter
import com.lumen.coacervation.engine.motion.morph.ContainerMorphController
import com.lumen.coacervation.engine.motion.morph.ContainerMorphHost
import com.lumen.coacervation.engine.motion.morph.ContainerMorphLauncher
import com.lumen.coacervation.engine.motion.morph.ContainerMorphOrigin
import com.lumen.coacervation.engine.widget.CoverableRippleDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

/** Each settings page is a native Activity; the engine owns the title and container transition. */
abstract class NativeSettingsActivity : Activity() {
    abstract val pageRoute: Route
    val repository: GlobalPreferencesRepository by lazy { KoinJavaComponent.get(GlobalPreferencesRepository::class.java) }
    val uiState: GlobalUIViewModel by lazy { KoinJavaComponent.get(GlobalUIViewModel::class.java) }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredPageState = savedInstanceState?.getBundle("native.page")
        renderingConfig = repository.uiConfigFlow.value
        renderingSkin = LumenEngine.requestedMaterial(this)
        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        ContainerMorphController.suppressSystemTransitions(this)
        lumen.prepare()
        pageUi = NativePageUi(this, restoredPageState)
        val page = buildPage()
        if (pageRoute == Route.Main) {
            setContentView(page)
            page.requestApplyInsets()
            lumen.bindRoot(page) { if (!isFinishing && !isDestroyed) recreate() }
        } else {
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            val host = ContainerMorphHost(this, lumen.palette.surface, lumen.palette.background,
                lumen.palette.textPrimary, toolbarTitle?.text?.toString().orEmpty())
            morphHost = host
            host.setMotionSurfaceBackground(lumen.motionSurfaceBackground(lumen.palette.surface, 16f))
            setContentView(host)
            host.replacePage(page, checkNotNull(toolbarTitle))
            // The page's chrome owns status-bar insets; padding the host would leave a material seam.
            host.requestApplyInsets()
            pageUi.backButton?.let(host::registerNavigationBack)
            lumen.bindRoot(host.liquidBackdropRoot()) { if (!isFinishing && !isDestroyed) recreate() }
            morph = ContainerMorphController(this, host, lumen, javaClass,
                ContainerMorphOrigin.from(intent), savedInstanceState == null, { toolbarTitle },
                collapsedCornerRadiusDp = 16f, isBusinessBlocked = { pageUi.hasUnsavedChanges })
            morph?.start(isFreshLaunch = savedInstanceState == null)
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        window.insetsController?.setSystemBarsAppearance(
            if (isDark()) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }

    private fun buildPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val chrome = FrameLayout(this).apply {
            id = R.id.native_chrome
            background = lumen.surface(lumen.palette.surface, 0f, SurfaceRole.TOP_BAR)
            elevation = dp(4).toFloat()
            addView(toolbar, FrameLayout.LayoutParams(-1, dp(68)))
        }
        if (pageRoute != Route.Main) {
            val back = pageUi.actionButton("‹", getString(R.string.native_back)) { requestBack() }
            pageUi.backButton = back
            toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        toolbarTitle = pageUi.text(intent.getStringExtra("native.title") ?: getString(pageTitleResource(pageRoute)), 22f, bold = true).apply {
            id = R.id.native_title
            setPadding(dp(10), 0, 0, 0)
        }
        toolbar.addView(toolbarTitle, LinearLayout.LayoutParams(0, -2, 1f))
        if (pageRoute == Route.Main) toolbar.addView(pageUi.actionButton("↻", getString(R.string.restart_title)) {
            pageUi.showRestartDialog()
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(chrome, LinearLayout.LayoutParams(-1, dp(68)))
        val body = when (pageRoute) {
            Route.Main -> pageUi.buildMainPage()
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
        val viewport = NativeContentViewport(this).apply {
            id = R.id.native_content_viewport
            addView(body, FrameLayout.LayoutParams(-1, -1))
        }
        page.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        val configuration = resources.configuration
        val split = renderingConfig.isSplitScreenEnabled &&
            (configuration.screenWidthDp > configuration.screenHeightDp ||
                configuration.screenWidthDp >= 840 && configuration.screenHeightDp >= 480)
        page.setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val left = if (!split || pageRoute == Route.Main) safe.left else 0
            val right = if (!split || pageRoute != Route.Main) safe.right else 0
            val chromeParams = chrome.layoutParams as LinearLayout.LayoutParams
            val height = dp(68) + safe.top
            if (chromeParams.height != height) { chromeParams.height = height; chrome.layoutParams = chromeParams }
            val toolbarParams = toolbar.layoutParams as FrameLayout.LayoutParams
            if (toolbarParams.topMargin != safe.top) { toolbarParams.topMargin = safe.top; toolbar.layoutParams = toolbarParams }
            toolbar.setPaddingRelative(dp(12) + left, dp(6), dp(12) + right, dp(6))
            viewport.setPadding(left, 0, right, safe.bottom)
            insets
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
                    setOnApplyWindowInsetsListener { _, insets ->
                        val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                        setPadding(safe.left, safe.top, 0, safe.bottom)
                        insets
                    }
                }, LinearLayout.LayoutParams(0, -1, 1f))
                addView(page, LinearLayout.LayoutParams(0, -1, 2f))
            }
            clipChildren = false; clipToPadding = false
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
        closePage()
    }

    fun updatePageTitle(title: String) { toolbarTitle?.text = title }

    private fun closePage() { morph?.commitBack() ?: finish() }
    fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    fun isDark(): Boolean = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == 401) BackupUtils.handleCreateDocument(this, data?.data)
        if (requestCode == 402) BackupUtils.handleReadDocument(this, data?.data)
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
        val primary = if (dynamic) getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
            else if (dark) Color.rgb(156, 199, 255) else Color.rgb(36, 105, 206)
        return LumenPalette(primary, if (dark) Color.BLACK else Color.WHITE,
            primary, primary, if (dark) Color.rgb(29, 31, 36) else Color.rgb(249, 250, 253),
            if (dark) Color.rgb(15, 18, 24) else Color.rgb(236, 242, 251),
            if (dark) Color.rgb(43, 47, 55) else Color.rgb(225, 232, 243),
            if (dark) Color.rgb(237, 240, 246) else Color.rgb(28, 31, 38),
            if (dark) Color.rgb(167, 175, 189) else Color.rgb(97, 107, 123))
    }

    override fun onStart() {
        super.onStart()
        lumen.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (renderingConfig != repository.uiConfigFlow.value || renderingSkin != LumenEngine.requestedMaterial(this)) {
            recreate()
            return
        }
        FairMemorySessionStore.remember(pageRoute)
        uiState.refreshRestartState()
        pageUi.refresh()
        startedJob?.cancel()
        startedJob = uiScope.launch {
            launch { uiState.state.collect { pageUi.refresh() } }
            launch { repository.preferenceUpdates.collect { pageUi.refresh() } }
            launch { repository.globalReloadEvent.collect { pageUi.onPreferencesReloaded() } }
            launch { pageUi.observeUpdates() }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lumen.onDispatchTouchEvent(event)
        return elastic.dispatch(event) { super.dispatchTouchEvent(it) }
    }

    override fun onPause() { startedJob?.cancel(); startedJob = null; elastic.clear(); super.onPause() }
    override fun onStop() { startedJob?.cancel(); startedJob = null; elastic.clear(); lumen.onStop(); super.onStop() }
    override fun onTrimMemory(level: Int) { lumen.onTrimMemory(level); super.onTrimMemory(level) }
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() { lumen.onLowMemory(); super.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("native.page", pageUi.saveState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        uiScope.cancel()
        pageUi.dispose()
        morph?.onDestroy()
        modals.onDestroy()
        elastic.dispose()
        lumen.onDestroy()
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
