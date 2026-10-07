package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.BGColorModes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ChangeBGColorTypes
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils
import com.highcapable.kavaref.extension.toClassOrNull

fun NativePageUi.buildBackgroundPage(): View = scrollContent {
    addSetting(header(R.drawable.demo_background, "BACKGROUND"))
    addSetting(intChoice(R.string.change_bg_color, Preferences.Background.CHANG_BG_COLOR_TYPE,
        ChangeBGColorTypes.entries.map { it.stringID }))
    addSetting(conditional({ repo.get(Preferences.Background.CHANG_BG_COLOR_TYPE) in 1..2 }) {
        addSetting(intChoice(R.string.color_mode, Preferences.Background.BG_COLOR_MODE,
            BGColorModes.entries.map { it.stringID }, if (DeviceUtils.isHyperOS) R.string.color_mode_tips else null) {
            if (DeviceUtils.isHyperOS && it == BGColorModes.FollowSystem.ordinal) write(Preferences.Background.IGNORE_DARK_MODE, true)
        })
    })
    addSetting(conditional({ repo.get(Preferences.Background.CHANG_BG_COLOR_TYPE) == ChangeBGColorTypes.FromCustom.ordinal }) {
        addSetting(navigation(R.string.set_custom_bg_color, Route.ColorPicker()))
    })
    addSetting(conditional({ repo.get(Preferences.Background.CHANG_BG_COLOR_TYPE) != ChangeBGColorTypes.NotChangeBGColor.ordinal }) {
        addSetting(switch(R.string.skip_app_with_bg_color, Preferences.Background.SKIP_APP_WITH_BG_COLOR))
        addSetting(navigation(R.string.change_bg_color_list, Route.BackgroundExcept))
    })
    addSetting(navigation(R.string.configure_bg_colors_individually, Route.BgIndividual))
    if (DeviceUtils.isHyperOS) {
        addSetting(switch(R.string.ignore_dark_mode, Preferences.Background.IGNORE_DARK_MODE, R.string.ignore_dark_mode_tips))
        if ("android.app.TaskSnapshotHelperImpl".toClassOrNull() != null) {
            addSetting(switch(R.string.remove_bg_drawable, Preferences.Background.REMOVE_BG_DRAWABLE, R.string.remove_bg_drawable_tips))
        }
    }
}
