package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.AppListPage

/**
 * 背景 - 替换背景颜色 - 排除列表
 */
@Composable
fun BackgroundExceptPage() {
    AppListPage(
        stringResource(R.string.background_except_title),
        Preferences.AppList.BG_EXCEPT_LIST
    )
}
