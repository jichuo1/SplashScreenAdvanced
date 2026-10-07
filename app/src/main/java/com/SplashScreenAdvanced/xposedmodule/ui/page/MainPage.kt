package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModulePreferenceRes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModuleStatusType
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NativePageUi.buildMainPage(stateKey: String = "scrollY"): View = scrollContent(stateKey) {
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
    addSetting(status)
    ModulePreferenceRes.entries.forEach { entry ->
        val row = navigation(entry.stringRes, checkNotNull(entry.navigateTo), moduleRequired = false)
        (row as com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow).addView(android.widget.ImageView(activity).apply {
            setImageResource(entry.iconRes)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 0, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        if (entry == ModulePreferenceRes.DevSettings) bind { row.visibility = if (activity.uiState.devMode) View.VISIBLE else View.GONE }
        addSetting(row)
    }
    addSetting(text(string(R.string.main_activity_hint), 13f, secondary = true))
}
