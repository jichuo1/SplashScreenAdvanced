package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.app.LocaleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.LocaleList
import android.view.View
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId

fun NativePageUi.buildBasicPage(): View {
    if (repo.get(Preferences.Log.ENABLE_LOG) &&
        System.currentTimeMillis() - repo.get(Preferences.Log.ENABLE_LOG_TIMESTAMP) > 86400000L) {
        write(Preferences.Log.ENABLE_LOG, false)
    }
    return scrollContent {
        addSetting(header(R.drawable.demo_basic, "BASIC"))
        addSetting(switch(R.string.enable_log, Preferences.Log.ENABLE_LOG, R.string.enable_log_tips) {
            if (it) write(Preferences.Log.ENABLE_LOG_TIMESTAMP, System.currentTimeMillis())
        })
        addSetting(switch(R.string.hide_icon, Preferences.Icon.ENABLE_HIDE_ICON) {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity, BuildConfig.APPLICATION_ID + ".Home"),
                if (it) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
        })
        addSetting(intChoice(R.string.lumen_palette, Preferences.Module.UI_STYLE,
            listOf(R.string.lumen_palette_classic, R.string.lumen_palette_dynamic), R.string.lumen_palette_summary,
            moduleRequired = false) { activity.recreate() })
        addSetting(choice(R.string.lumen_material, {
            listOf(NativeChoice(SkinId.MATERIAL_YOU, string(R.string.lumen_material_soft)),
                NativeChoice(SkinId.LIQUID, string(R.string.lumen_material_liquid)))
        }, { LumenEngine.requestedMaterial(activity) }, R.string.lumen_material_summary, moduleRequired = false) {
            if (LumenEngine.selectMaterial(activity, it, it == SkinId.LIQUID && repo.get(Preferences.Module.MODULE_APP_BLUR))) activity.recreate()
            else activity.toast(R.string.save_failed)
        })
        val localeManager = activity.getSystemService(LocaleManager::class.java)
        val languageTags = listOf("", "zh-CN", "zh-TW", "en", "ja", "es", "ru", "fr")
        val languageLabels = listOf(R.string.language_follow_system, R.string.language_zh_cn, R.string.language_zh_tw,
            R.string.language_en, R.string.language_ja, R.string.language_es, R.string.language_ru, R.string.language_fr)
        addSetting(choice(R.string.app_language, {
            languageTags.mapIndexed { index, tag -> NativeChoice(tag, string(languageLabels[index])) }
        }, { localeManager.applicationLocales.toLanguageTags() }, moduleRequired = false) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(it)
        })
        addSetting(switch(R.string.lumen_capture, Preferences.Module.MODULE_APP_BLUR, R.string.lumen_capture_summary,
            enabled = { LumenEngine.requestedMaterial(activity) == SkinId.LIQUID }) {
            if (LumenEngine.setRealtimeCaptureEnabled(activity, it)) activity.recreate() else activity.toast(R.string.save_failed)
        })
        addSetting(switch(R.string.split_view, Preferences.Module.SPLIT_VIEW, R.string.split_view_tips) { activity.recreate() })
        addSetting(action(string(R.string.backup)) { activity.launchBackup() })
        addSetting(action(string(R.string.restore)) { activity.launchRestore() })
    }
}
