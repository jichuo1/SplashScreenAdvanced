package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.AppListPage
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import com.SplashScreenAdvanced.xposedmodule.utils.RemotePreferenceStore
import dev.lackluster.hyperx.ui.preference.PreferenceGroup
import org.koin.compose.koinInject

/**
 * 图标 - 不显示图标
 */
@Composable
fun HideIconPage() {
    val store = koinInject<RemotePreferenceStore>()
    var exceptionMode by remember {
        mutableStateOf(store.get(Preferences.Scope.IS_HIDE_SPLASH_SCREEN_ICON_EXCEPTION_MODE))
    }
    val exceptionSummary = stringResource(
        R.string.exception_mode_message,
        if (exceptionMode)
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
                    key = Preferences.Scope.IS_HIDE_SPLASH_SCREEN_ICON_EXCEPTION_MODE,
                    onCheckedChange = { exceptionMode = it }
                )
            }
        }
    }
}
