package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModulePreferenceRes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModuleStatusType
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dock home page: module status, then the switches that decide which apps are handled at all.
 * [withRoutes] keeps the route list reachable in the adaptive two-pane layout, where detail pages open beside it.
 */
fun NativePageUi.buildHomePage(withRoutes: Boolean): View = scrollContent("scroll") {
    addSetting(statusCard())
    scopeSection(this)
    if (withRoutes) routeRows().forEach { addSetting(it) }
}

/** Master list of the adaptive two-pane layout: status followed by one row per route page. */
fun NativePageUi.buildMainPage(stateKey: String = "scrollY"): View = scrollContent(stateKey) {
    addSetting(statusCard())
    routeRows().forEach { addSetting(it) }
    addSetting(text(string(R.string.main_activity_hint), 13f, secondary = true))
}

private fun NativePageUi.statusCard(): NativeRow {
    val status = row("")
    status.isLongClickable = true
    status.setOnLongClickListener {
        if (activity.uiState.moduleActive) activity.uiScope.launch {
            withContext(Dispatchers.IO) {
                execShell("am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android")
            }
        }
        true
    }
    bind {
        val ui = activity.uiState.state.value
        val type = when {
            ui.moduleActive && ui.androidRestartNeeded == true -> ModuleStatusType.ACTIVE_ANDROID_RESTART
            ui.moduleActive && ui.systemUIRestartNeeded -> ModuleStatusType.ACTIVE_SYSTEM_UI_RESTART
            ui.moduleActive -> ModuleStatusType.ACTIVE_NO_NEED_RESTART
            else -> ModuleStatusType.INACTIVE
        }
        status.titleView.text = string(type.stateTextRes)
        status.summaryView.visibility = View.VISIBLE
        status.summaryView.text = string(R.string.module_version, BuildConfig.VERSION_NAME) +
            if (ui.moduleActive) System.lineSeparator() +
                string(R.string.xposed_framework_version, ui.xposedFrameworkName, ui.xposedApiVersion) else ""
    }
    return status
}

private fun NativePageUi.routeRows(): List<View> = ModulePreferenceRes.entries.map { entry ->
    val row = navigation(entry.stringRes, checkNotNull(entry.navigateTo), moduleRequired = false) as NativeRow
    row.addView(ImageView(activity).apply {
        setImageResource(entry.iconRes)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, 0, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
    if (entry == ModulePreferenceRes.DevSettings) bind {
        row.visibility = if (activity.uiState.devMode) View.VISIBLE else View.GONE
    }
    row
}
