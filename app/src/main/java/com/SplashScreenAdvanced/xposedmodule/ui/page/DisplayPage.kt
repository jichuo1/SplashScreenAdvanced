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
 * 显示设置 界面
 */
@Composable
fun DisplayPage() {
    val navigator = LocalNavigator.current
    HyperXPage(
        title = stringResource(R.string.display_settings),
    ) {
        item(key = "header") {
            HeaderCard(imageResID = R.drawable.demo_display, title = "DISPLAY")
        }
        itemPreferenceGroup(key = "min-duration") {
            TextPreference(
                title = stringResource(R.string.min_duration),
                summary = stringResource(R.string.min_duration_tips),
                onClick = { navigator.push(Route.MinDuration) }
            )
        }
        itemPreferenceGroup(key = "force-show") {
            ForceShowSplashScreenSettingsGroup(navigator)
        }
        itemPreferenceGroup(key = "other", position = ItemPosition.Last) {
            OtherDisplaySettingsGroup()
        }
    }
}

/**
 * 强制显示遮罩
 */
@Composable
private fun ForceShowSplashScreenSettingsGroup(navigator: Navigator) {
    val context = LocalContext.current
    val forceShowSplash = rememberPreferenceState(Preferences.Display.FORCE_SHOW_SPLASH_SCREEN)
    SwitchPreference(
        title = stringResource(R.string.force_show_splash_screen),
        summary = stringResource(R.string.force_show_splash_screen_tips),
        checked = forceShowSplash
    ) { newValue ->
        if (newValue) {
            context.toast(R.string.custom_scope_message)
        }
    }
    AnimatedVisibility(
        visible = forceShowSplash.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            TextPreference(title = stringResource(R.string.force_show_splash_screen_list)) {
                navigator.push(Route.ForceSplash)
            }
            SwitchPreference(
                title = stringResource(R.string.reduce_splash_screen),
                summary = stringResource(R.string.reduce_splash_screen_tips),
                key = Preferences.Display.REDUCE_SPLASH_SCREEN
            )
        }
    }
}

/**
 * 其他显示设置组
 */
@Composable
private fun OtherDisplaySettingsGroup() {
    val forceDisableSplash = rememberPreferenceState(Preferences.Display.DISABLE_SPLASH_SCREEN)
    val forceEnableSplash = rememberPreferenceState(Preferences.Display.FORCE_ENABLE_SPLASH_SCREEN)

    AnimatedVisibility(
        visible = !forceDisableSplash.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            SwitchPreference(
                title = stringResource(R.string.force_enable_splash_screen),
                summary = stringResource(R.string.force_enable_splash_screen_tips),
                checked = forceEnableSplash
            )
            AnimatedVisibility(
                visible = forceEnableSplash.value,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.hot_start_compatible),
                    summary = stringResource(R.string.hot_start_compatible_tips),
                    key = Preferences.Display.ENABLE_HOT_START_COMPATIBLE,
                    enabled = forceEnableSplash.value
                )
            }
        }
    }
    SwitchPreference(
        title = stringResource(R.string.disable_splash_screen),
        summary = stringResource(R.string.disable_splash_screen_tips),
        checked = forceDisableSplash
    )
}
