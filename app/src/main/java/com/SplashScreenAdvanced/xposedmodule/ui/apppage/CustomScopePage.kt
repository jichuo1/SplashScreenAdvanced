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
 * 作用域 - 自定义模块作用域 - 配置应用列表
 */
@Composable
fun CustomScopePage() {
    val exceptionMode = rememberPreferenceState(Preferences.Scope.IS_CUSTOM_SCOPE_EXCEPTION_MODE)
    val exceptionSummary = stringResource(
        R.string.custom_scope_exception_mode_message,
        if (exceptionMode.value)
            stringResource(R.string.will_not)
        else
            stringResource(R.string.will_only)
    )
    AppListPage(
        stringResource(R.string.custom_scope_title),
        Preferences.AppList.CUSTOM_SCOPE_LIST
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
