package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.AppListPage
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import dev.lackluster.hyperx.ui.preference.PreferenceGroup
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState

/**
 * 图标 - 不显示图标
 */
@Composable
fun HideIconPage() {
    val exceptionMode = rememberPreferenceState(Preferences.Scope.IS_HIDE_SPLASH_SCREEN_ICON_EXCEPTION_MODE)
    val exceptionSummary = stringResource(
        R.string.exception_mode_message,
        if (exceptionMode.value)
            stringResource(R.string.not_chosen)
        else
            stringResource(R.string.chosen)
    )
    AppListPage(
        stringResource(R.string.hide_splash_screen_icon_title),
        Preferences.AppList.HIDE_SPLASH_SCREEN_ICON_LIST
    ) {
        item {
            PreferenceGroup {
                SwitchPreference(
                    title = stringResource(R.string.exception_mode),
                    summary = exceptionSummary,
                    checked = exceptionMode
                )
            }
        }
    }
}
