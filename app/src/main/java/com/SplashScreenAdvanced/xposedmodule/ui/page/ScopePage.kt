package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.utils.toast

fun NativePageUi.buildScopePage(): View = scrollContent {
    addSetting(header(R.drawable.demo_scope, "SCOPE"))
    addSetting(switch(R.string.custom_scope, Preferences.Scope.ENABLE_CUSTOM_SCOPE) {
        if (it) activity.toast(R.string.custom_scope_message)
    })
    addSetting(conditional({ repo.get(Preferences.Scope.ENABLE_CUSTOM_SCOPE) }) {
        addSetting(switch(R.string.replace_to_empty_splash_screen, Preferences.Icon.REPLACE_TO_EMPTY_SPLASH_SCREEN,
            R.string.replace_to_empty_splash_screen_tips))
        addSetting(navigation(R.string.exception_mode_list, Route.CustomScope))
    })
}
