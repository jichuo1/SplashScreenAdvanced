package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ShrinkIconType
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils
import com.SplashScreenAdvanced.xposedmodule.utils.IconPackManager
import com.SplashScreenAdvanced.xposedmodule.utils.sr.IconCacheStore
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NativePageUi.buildIconPage(): View {
    var packs = listOf(NativeChoice("None", "None"))
    activity.uiScope.launch {
        packs = withContext(Dispatchers.IO) {
            listOf(NativeChoice("None", "None")) + IconPackManager(activity).getAvailableIconPacks()
                .filterKeys { it != "None" }.map { NativeChoice(it.key, it.value, it.key) }
        }
        if (packs.none { it.value == repo.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME) }) {
            write(Preferences.Icon.ICON_PACK_PACKAGE_NAME, "None")
            activity.toast(R.string.icon_pack_is_removed)
        }
        refresh()
    }
    return scrollContent {
        addSetting(header(R.drawable.demo_icon, "ICON"))
        addSetting(switch(R.string.draw_round_corner, Preferences.Display.ENABLE_DRAW_ROUND_CORNER))
        addSetting(intChoice(R.string.shrink_icon, Preferences.Icon.SHRINK_ICON, ShrinkIconType.entries.map { it.stringID }))
        addSetting(conditional({ repo.get(Preferences.Icon.SHRINK_ICON) != 0 }) {
            addSetting(switch(R.string.add_icon_blur_bg, Preferences.Icon.ENABLE_ADD_ICON_BLUR_BG))
        })
        addSetting(switch(R.string.replace_icon, Preferences.Icon.ENABLE_REPLACE_ICON, R.string.replace_icon_tips))
        addSetting(intChoice(R.string.icon_enhance, Preferences.Icon.ENHANCE_LEVEL,
            listOf(R.string.icon_enhance_off, R.string.icon_enhance_standard, R.string.icon_enhance_high,
                R.string.icon_enhance_ultra), R.string.icon_enhance_tips))
        addSetting(conditional({ repo.get(Preferences.Icon.ENHANCE_LEVEL) != 0 }) {
            addSetting(switch(R.string.icon_enhance_gpu, Preferences.Icon.ENHANCE_GPU, R.string.icon_enhance_gpu_tips))
        })
        val scan = action(string(R.string.sr_factory), string(R.string.sr_factory_tips)) { activity.requestScanPermission() }
        addSetting(scan)
        whileVisible {
            while (true) {
                val stats = withContext(Dispatchers.IO) {
                    IconCacheStore.readIndex(activity).entries.size to IconCacheStore.totalBytes(activity) / 1024f / 1024f
                }
                scan.summaryView.text = string(R.string.sr_factory_tips) + System.lineSeparator() +
                    string(R.string.icon_cache_stats, stats.first, stats.second)
                delay(2000)
            }
        }
        if (DeviceUtils.isHyperOS) {
            addSetting(switch(R.string.remove_icon_stroke, Preferences.Icon.ENABLE_REMOVE_ICON_STROKE))
            addSetting(switch(R.string.use_miui_large_icon, Preferences.Icon.ENABLE_USE_MIUI_LARGE_ICON))
        }
        addSetting(choice(R.string.use_icon_pack, { packs }, { repo.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME) }) {
            write(Preferences.Icon.ICON_PACK_PACKAGE_NAME, it)
        })
        addSetting(switch(R.string.icon_foreground_only, Preferences.Icon.ICON_FOREGROUND_ONLY, R.string.icon_foreground_only_tips))
        addSetting(switch(R.string.default_style, Preferences.Icon.ENABLE_DEFAULT_STYLE, R.string.default_style_tips) {
            if (it) activity.toast(R.string.custom_scope_message)
        })
        addSetting(conditional({ repo.get(Preferences.Icon.ENABLE_DEFAULT_STYLE) }) {
            addSetting(navigation(R.string.default_style_list, Route.IgnoreAppIcon))
        })
        addSetting(switch(R.string.hide_splash_screen_icon, Preferences.Icon.ENABLE_HIDE_SPLASH_SCREEN_ICON) {
            if (it) activity.toast(R.string.custom_scope_message)
        })
        addSetting(conditional({ repo.get(Preferences.Icon.ENABLE_HIDE_SPLASH_SCREEN_ICON) }) {
            addSetting(navigation(R.string.default_style_list, Route.HideIcon))
        })
    }
}
