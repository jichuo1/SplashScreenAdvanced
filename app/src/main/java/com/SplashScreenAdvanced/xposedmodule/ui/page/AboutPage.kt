package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsSection
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager

/** Standalone About route (two-pane layout): the dock splits these between Home (updates) and Module (about). */
fun NativePageUi.buildAboutPage(): View = scrollContent {
    aboutSection(this)
    updateSection(this)
}

fun NativePageUi.aboutSection(parent: LinearLayout) = parent.section(SettingsSection.ModuleAbout) {
    var taps = 0
    var lastTap = 0L
    val brand = header(R.drawable.ic_launcher_foreground, string(R.string.app_name) + System.lineSeparator() +
        string(R.string.version, BuildConfig.VERSION_NAME))
    brand.setOnClickListener {
        val now = SystemClock.uptimeMillis()
        taps = if (now - lastTap < 500) taps + 1 else 1
        lastTap = now
        if (taps == 5) {
            taps = 0; write(Preferences.Dev.ENABLE_DEV_SETTINGS, true)
            activity.uiState.syncDevMode(); activity.toast(R.string.enable_dev_settings)
        }
    }
    addSetting(brand)
    addSetting(action("GitHub", string(R.string.open_source_repo)) { openUrl("https://github.com/jichuo1/SplashScreenAdvanced") })
    addSetting(action(string(R.string.upstream_project), string(R.string.upstream_project_summary)) {
        openUrl("https://github.com/GSWXXN/RestoreSplashScreen")
    }.tracked(R.string.upstream_project))
    // The reference rows would make up a third of the Module page; they fold behind one row.
    addSetting(expandable(string(R.string.open_source_license), value = OpenSourceReference.entries.size.toString(),
        stateKey = "licenses") {
        OpenSourceReference.entries.forEach { project ->
            addSetting(action(project.author + "/" + project.project, project.license) {
                activity.toast(string(R.string.thanks_to, project.author)); openUrl(project.link)
            })
        }
    })
}

fun NativePageUi.updateSection(parent: LinearLayout) = parent.section(SettingsSection.HomeUpdate) {
    addSetting(switch(R.string.update_auto_check, Preferences.Module.AUTO_UPDATE_CHECK,
        R.string.update_auto_check_tips, moduleRequired = false))
    addSetting(choice(R.string.update_channel, {
        listOf(NativeChoice(GitHubReleaseChecker.UpdateChannel.STABLE.storageValue, string(R.string.update_channel_stable),
            string(R.string.update_channel_stable_tips)),
            NativeChoice(GitHubReleaseChecker.UpdateChannel.PREVIEW.storageValue, string(R.string.update_channel_preview),
                string(R.string.update_channel_preview_tips)))
    }, { repo.get(Preferences.Module.UPDATE_CHANNEL) }, moduleRequired = false) { write(Preferences.Module.UPDATE_CHANNEL, it) })
    val update = action(string(R.string.update_check_now), moduleRequired = false) {
        if (!UpdateCheckManager.isChecking()) UpdateCheckManager.checkNow(activity) {
            GitHubReleaseChecker.UpdateChannel.fromStorageValue(repo.get(Preferences.Module.UPDATE_CHANNEL))
        }
    }.tracked(R.string.update_check_now)
    bind { update.summaryView.visibility = View.VISIBLE; update.summaryView.text =
        if (UpdateCheckManager.isChecking()) string(R.string.update_checking)
        else string(R.string.update_check_now_tips, BuildConfig.VERSION_NAME) }
    addSetting(update)
}

/** Libraries the app ships or builds on, and the projects it borrowed approaches from; the same list as the README credits. */
enum class OpenSourceReference(val author: String, val project: String, val license: String, val link: String) {
    LumenCoacervationEngine("Meguvizarchic", "LumenCoacervationEngine", "Apache-2.0", "https://github.com/Meguvizarchic/LumenCoacervationEngine"),
    MeguvizRef("Meguvizarchic", "MeguvizRef", "Apache-2.0", "https://github.com/Meguvizarchic/MeguvizRef"),
    MeguvizUI("Meguvizarchic", "MeguvizUI", "Apache-2.0", "https://github.com/Meguvizarchic/MeguvizUI"),
    MeguvizAndroid("Meguvizarchic", "MeguvizAndroid", "Apache-2.0", "https://github.com/Meguvizarchic/MeguvizAndroid"),
    MeguvizDrawable("Meguvizarchic", "MeguvizDrawable", "Apache-2.0", "https://github.com/Meguvizarchic/MeguvizDrawable"),
    Libxposed("libxposed", "api · service", "Apache-2.0", "https://github.com/libxposed"),
    DexKit("LuckyPray", "DexKit", "Apache-2.0", "https://github.com/LuckyPray/DexKit"),
    BetterAndroid("BetterAndroid", "BetterAndroid", "Apache-2.0", "https://github.com/BetterAndroid/BetterAndroid"),
    Koin("InsertKoinIO", "Koin", "Apache-2.0", "https://github.com/InsertKoinIO/koin"),
    Ncnn("Tencent", "ncnn", "BSD-3-Clause", "https://github.com/Tencent/ncnn"),
    Waifu2x("nihui", "waifu2x-ncnn-vulkan", "MIT", "https://github.com/nihui/waifu2x-ncnn-vulkan"),
    HideMyApplist("Dr-TSNG", "Hide-My-Applist", "AGPL-3.0", "https://github.com/Dr-TSNG/Hide-My-Applist"),
    MiuiHomeR("qqlittleice", "MiuiHome_R", "GPL-3.0", "https://github.com/qqlittleice/MiuiHome_R"),
    MIUINativeNotifyIcon("fankes", "MIUINativeNotifyIcon", "AGPL-3.0", "https://github.com/fankes/MIUINativeNotifyIcon"),
    YukiHookAPI("fankes", "YukiHookAPI", "Apache-2.0", "https://github.com/fankes/YukiHookAPI"),
}
