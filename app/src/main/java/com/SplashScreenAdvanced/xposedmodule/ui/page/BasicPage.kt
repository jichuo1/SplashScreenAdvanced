package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.app.LocaleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.LocaleList
import android.view.View
import android.widget.LinearLayout
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsSection
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.background.LiquidBackgroundMode
import com.lumen.coacervation.engine.background.LiquidBackgroundStore
import com.lumen.coacervation.engine.model.SkinId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NativePageUi.moduleAppearanceSection(parent: LinearLayout) = parent.section(SettingsSection.ModuleAppearance) {
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
    // Live backdrop only means something on the advanced glass: it unfolds under the material instead of sitting greyed out.
    addSetting(conditional({ LumenEngine.requestedMaterial(activity) == SkinId.LIQUID }) {
        addSetting(switch(R.string.lumen_capture, Preferences.Module.MODULE_APP_BLUR, R.string.lumen_capture_summary,
            moduleRequired = false, enabled = { LumenEngine.requestedMaterial(activity) == SkinId.LIQUID }) {
            if (LumenEngine.setRealtimeCaptureEnabled(activity, it)) activity.recreate() else activity.toast(R.string.save_failed)
        })
    })
    val background = action(string(R.string.module_background), string(R.string.module_background_summary), moduleRequired = false) {
        activity.launchBackgroundPicker()
    }.tracked(R.string.module_background)
    var customBackground = false
    bind {
        val state = LiquidBackgroundStore.read(activity)
        customBackground = state.config.mode == LiquidBackgroundMode.CUSTOM
        background.valueView.text = when (state.config.mode) {
            LiquidBackgroundMode.AUTOMATIC -> string(R.string.module_background_automatic)
            LiquidBackgroundMode.CUSTOM -> state.config.displayName ?: string(R.string.module_background_custom)
        }
    }
    addSetting(background)
    // Restoring is only possible once a custom picture is in use.
    addSetting(conditional({ customBackground }) {
        addSetting(action(string(R.string.module_background_restore), moduleRequired = false) {
            activity.uiScope.launch {
                val restored = withContext(Dispatchers.IO) { LiquidBackgroundStore.restoreAutomatic(activity) }
                if (restored) activity.recreate() else activity.toast(R.string.save_failed)
            }
        }.tracked(R.string.module_background_restore))
    })
    addSetting(switch(R.string.split_view, Preferences.Module.SPLIT_VIEW, R.string.split_view_tips) { activity.recreate() })
    val localeManager = activity.getSystemService(LocaleManager::class.java)
    val languageTags = listOf("", "zh-CN", "zh-TW", "en", "ja", "es", "ru", "fr")
    val languageLabels = listOf(R.string.language_follow_system, R.string.language_zh_cn, R.string.language_zh_tw,
        R.string.language_en, R.string.language_ja, R.string.language_es, R.string.language_ru, R.string.language_fr)
    addSetting(choice(R.string.app_language, {
        languageTags.mapIndexed { index, tag -> NativeChoice(tag, string(languageLabels[index])) }
    }, { localeManager.applicationLocales.toLanguageTags() }, moduleRequired = false) {
        localeManager.applicationLocales = LocaleList.forLanguageTags(it)
    })
}

fun NativePageUi.moduleSystemSection(parent: LinearLayout) {
    // Logging switches itself off a day after it was enabled; settle that before the switch binds.
    if (repo.get(Preferences.Log.ENABLE_LOG) &&
        System.currentTimeMillis() - repo.get(Preferences.Log.ENABLE_LOG_TIMESTAMP) > 86400000L) {
        write(Preferences.Log.ENABLE_LOG, false)
    }
    parent.section(SettingsSection.ModuleSystem) {
        addSetting(switch(R.string.hide_icon, Preferences.Icon.ENABLE_HIDE_ICON) {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity, BuildConfig.APPLICATION_ID + ".Home"),
                if (it) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
        })
        addSetting(switch(R.string.enable_log, Preferences.Log.ENABLE_LOG, R.string.enable_log_tips) {
            if (it) write(Preferences.Log.ENABLE_LOG_TIMESTAMP, System.currentTimeMillis())
        })
        // Developer options stay out of search and out of sight until the easter egg on the About card unlocks them.
        val developer = navigation(R.string.dev_settings, Route.Developer, moduleRequired = false, searchable = false)
        bind { developer.visibility = if (activity.uiState.devMode) View.VISIBLE else View.GONE }
        addSetting(developer)
    }
}

fun NativePageUi.moduleBackupSection(parent: LinearLayout) = parent.section(SettingsSection.ModuleBackup) {
    addSetting(action(string(R.string.backup)) { activity.launchBackup() }.tracked(R.string.backup))
    addSetting(action(string(R.string.restore)) { activity.launchRestore() }.tracked(R.string.restore))
}

fun NativePageUi.buildBasicPage(): View = scrollContent {
    addSetting(header(R.drawable.demo_basic, "BASIC"))
    moduleAppearanceSection(this)
    moduleSystemSection(this)
    moduleBackupSection(this)
}

/**
 * Dock page: the module itself — look, system, backup and about. Updates live on Home next to the status card, and the
 * licenses fold behind one row, so this page no longer carries twice the rows of any other tab.
 */
fun NativePageUi.buildModulePage(): View = scrollContent {
    addSetting(header(R.drawable.demo_basic, "MODULE"))
    moduleAppearanceSection(this)
    moduleSystemSection(this)
    moduleBackupSection(this)
    aboutSection(this)
}
