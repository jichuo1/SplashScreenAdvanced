package com.SplashScreenAdvanced.xposedmodule.ui

import android.content.Intent
import android.os.Bundle
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemorySessionStore
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeSettingsActivity
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.activityForRoute
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager
import kotlinx.coroutines.launch

class MainActivity : NativeSettingsActivity() {
    override val pageRoute = Route.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val restored = FairMemorySessionStore.consumeRestoreBackStack(this).last()
            if (restored != Route.Main && restored != Route.Empty) window.decorView.post {
                startActivity(Intent(this, activityForRoute(restored)).apply {
                    if (restored is Route.ColorPicker) putExtra("native.package", restored.pkgName)
                })
            }
        }
        uiScope.launch {
            UpdateCheckManager.maybeAutoCheck(this@MainActivity,
                enabled = { repository.get(Preferences.Module.AUTO_UPDATE_CHECK) },
                channelProvider = { GitHubReleaseChecker.UpdateChannel.fromStorageValue(
                    repository.get(Preferences.Module.UPDATE_CHANNEL)) })
        }
    }
}
