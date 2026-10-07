package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.os.SystemClock
import android.view.View
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.header
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager

fun NativePageUi.buildAboutPage(): View = scrollContent {
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
    })
    addSetting(text(string(R.string.update_settings), 14f, secondary = true))
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
    }
    bind { update.summaryView.visibility = View.VISIBLE; update.summaryView.text =
        if (UpdateCheckManager.isChecking()) string(R.string.update_checking)
        else string(R.string.update_check_now_tips, BuildConfig.VERSION_NAME) }
    addSetting(update)
    addSetting(text(string(R.string.open_source_license), 14f, secondary = true))
    OpenSourceReference.entries.forEach { project ->
        addSetting(action(project.author + "/" + project.name, project.license) {
            activity.toast(string(R.string.thanks_to, project.author)); openUrl(project.link)
        })
    }
}

enum class OpenSourceReference(val author: String, val license: String, val link: String) {
    LumenCoacervationEngine("jichuo1", "Apache-2.0", "https://github.com/jichuo1/LumenCoacervationEngine"),
    MIUINativeNotifyIcon("fankes", "AGPL-3.0", "https://github.com/fankes/MIUINativeNotifyIcon"),
    `Hide-My-Applist`("Dr-TSNG", "AGPL-3.0", "https://github.com/Dr-TSNG/Hide-My-Applist"),
    YukiHookAPI("fankes", "Apache-2.0", "https://github.com/fankes/YukiHookAPI"),
    HyperCompose("HowieHChen", "Apache-2.0", "https://github.com/HowieHChen/hyperx-compose"),
    Miuix("miuix-kotlin-multiplatform", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"),
    DexKit("LuckyPray", "LGPL-3.0", "https://github.com/LuckyPray/DexKit"),
}
