package com.SplashScreenAdvanced.xposedmodule.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.HeaderCard
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup

/**
 * 作用域 界面
 */
@Composable
fun ScopePage() {
    val navigator = LocalNavigator.current
    HyperXPage(
        title = stringResource(R.string.custom_scope_settings),
    ) {
        item(key = "header") {
            HeaderCard(imageResID = R.drawable.demo_scope, title = "SCOPE")
        }
        itemPreferenceGroup(key = "settings", position = ItemPosition.Last) {
            SettingItems(navigator)
        }
    }
}

/**
 * 作用阈设置项
 */
@Composable
private fun SettingItems(navigator: Navigator) {
    val context = LocalContext.current
    val customScope = rememberPreferenceState(Preferences.Scope.ENABLE_CUSTOM_SCOPE)

    // 自定义模块作用域
    SwitchPreference(
        title = stringResource(R.string.custom_scope),
        checked = customScope
    ) { newValue ->
        if (newValue) {
            context.toast(R.string.custom_scope_message)
        }
    }
    AnimatedVisibility(
        visible = customScope.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            // 将作用域外的应用替换位空白启动遮罩
            SwitchPreference(
                title = stringResource(R.string.replace_to_empty_splash_screen),
                summary = stringResource(R.string.replace_to_empty_splash_screen_tips),
                key = Preferences.Icon.REPLACE_TO_EMPTY_SPLASH_SCREEN
            )
            // 配置应用列表
            TextPreference(title = stringResource(R.string.exception_mode_list)) {
                navigator.push(Route.CustomScope)
            }
        }
    }
}
