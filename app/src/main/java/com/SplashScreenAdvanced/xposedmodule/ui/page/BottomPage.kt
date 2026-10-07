package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.utils.toast

fun NativePageUi.buildBottomPage(): View = scrollContent {
    addSetting(header(R.drawable.demo_branding, "BRANDING IMAGE"))
    addSetting(switch(R.string.remove_branding_image, Preferences.Display.REMOVE_BRANDING_IMAGE,
        R.string.remove_branding_image_tips) { if (it) activity.toast(R.string.custom_scope_message) })
    addSetting(conditional({ repo.get(Preferences.Display.REMOVE_BRANDING_IMAGE) }) {
        addSetting(navigation(R.string.remove_branding_image_list, Route.RemoveBranding))
    })
}
