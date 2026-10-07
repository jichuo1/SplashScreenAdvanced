package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.utils.toast

fun NativePageUi.buildDisplayPage(): View = scrollContent {
    addSetting(header(R.drawable.demo_display, "DISPLAY"))
    addSetting(navigation(R.string.min_duration, Route.MinDuration, R.string.min_duration_tips))
    addSetting(switch(R.string.force_show_splash_screen, Preferences.Display.FORCE_SHOW_SPLASH_SCREEN,
        R.string.force_show_splash_screen_tips) { if (it) activity.toast(R.string.custom_scope_message) })
    addSetting(conditional({ repo.get(Preferences.Display.FORCE_SHOW_SPLASH_SCREEN) }) {
        addSetting(navigation(R.string.force_show_splash_screen_list, Route.ForceSplash))
        addSetting(switch(R.string.reduce_splash_screen, Preferences.Display.REDUCE_SPLASH_SCREEN,
            R.string.reduce_splash_screen_tips))
    })
    addSetting(conditional({ !repo.get(Preferences.Display.DISABLE_SPLASH_SCREEN) }) {
        addSetting(switch(R.string.force_enable_splash_screen, Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN,
            R.string.force_enable_splash_screen_tips))
        addSetting(conditional({ repo.get(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN) }) {
            addSetting(switch(R.string.hot_start_compatible, Preferences.Display.ENABLE_HOT_START_COMPATIBLE,
                R.string.hot_start_compatible_tips))
        })
    })
    addSetting(switch(R.string.disable_splash_screen, Preferences.Display.DISABLE_SPLASH_SCREEN,
        R.string.disable_splash_screen_tips))
}
