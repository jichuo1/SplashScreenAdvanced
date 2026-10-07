package com.SplashScreenAdvanced.xposedmodule.ui.component

import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.showUpdateDialog
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun NativePageUi.observeNativeUpdates() {
    UpdateCheckManager.state.collect { state ->
        refresh()
        when (state) {
            UpdateCheckManager.UpdateCheckState.Latest -> {
                activity.toast(R.string.update_latest); UpdateCheckManager.dismiss()
            }
            UpdateCheckManager.UpdateCheckState.Failed -> {
                activity.toast(R.string.update_check_failed); UpdateCheckManager.dismiss()
            }
            is UpdateCheckManager.UpdateCheckState.NewVersion -> {
                val release = state.release
                showUpdateDialog(release, onDismissed = { UpdateCheckManager.dismiss() }) {
                    activity.uiScope.launch {
                        val target = runCatching { withContext(Dispatchers.IO) {
                            GitHubReleaseChecker.resolveReleaseDetailsDestination(release.htmlUrl).url
                        } }.getOrDefault(release.htmlUrl)
                        openUrl(target)
                    }
                }
            }
            else -> Unit
        }
    }
}
