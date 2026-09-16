package com.SplashScreenAdvanced.xposedmodule.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.DropDownPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.HeaderCard
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.BGColorModes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ChangeBGColorTypes
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils.isHyperOS
import com.highcapable.kavaref.extension.toClassOrNull
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.DropDownEntry
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup

/**
 * 背景 界面
 */
@Composable
fun BackgroundPage() {
    val navigator = LocalNavigator.current
    val ignoreDarkMode = rememberPreferenceState(Preferences.Background.IGNORE_DARK_MODE)
    HyperXPage(
        title = stringResource(R.string.background_settings),
    ) {
        item(key = "header") {
            HeaderCard(imageResID = R.drawable.demo_background, title = "BACKGROUND")
        }
        itemPreferenceGroup(
            key = "general",
            position = if (isHyperOS) ItemPosition.Middle else ItemPosition.Last
        ) {
            GeneralSettingItems(navigator = navigator, ignoreDarkMode = ignoreDarkMode)
        }
        if (isHyperOS) {
            itemPreferenceGroup(key = "miui", position = ItemPosition.Last) {
                MIUISettingsGroup(ignoreDarkMode = ignoreDarkMode)
            }
        }
    }
}

/**
 * 替换背景颜色的通用设置
 */
@Composable
private fun GeneralSettingItems(
    navigator: Navigator,
    ignoreDarkMode: MutableState<Boolean>
) {
    val colorMode = rememberPreferenceState(Preferences.Background.BG_COLOR_MODE)
    val changeBGColorType = rememberPreferenceState(Preferences.Background.CHANG_BG_COLOR_TYPE)

    val shouldShowColorMode = changeBGColorType.value == ChangeBGColorTypes.FromIcon.ordinal ||
            changeBGColorType.value == ChangeBGColorTypes.FromMonet.ordinal

    // 替换背景颜色
    DropDownPreference(
        title = stringResource(R.string.change_bg_color),
        entries = ChangeBGColorTypes.entries.mapIndexed { index, type -> DropDownEntry(value = index, title = stringResource(type.stringID)) },
        selectedIndex = changeBGColorType
    )

    AnimatedVisibility(
        visible = shouldShowColorMode,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // 颜色模式
        DropDownPreference(
            title = stringResource(R.string.color_mode),
            summary = if (isHyperOS) stringResource(R.string.color_mode_tips) else null,
            entries = BGColorModes.entries.mapIndexed { index, mode -> DropDownEntry(value = index, title = stringResource(mode.stringID)) },
            selectedIndex = colorMode
        ) {
            if (isHyperOS && colorMode.value == BGColorModes.FollowSystem.ordinal) {
                ignoreDarkMode.value = true
            }
        }
    }
    AnimatedVisibility(
        visible = changeBGColorType.value == ChangeBGColorTypes.FromCustom.ordinal,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // 自定义背景颜色
        TextPreference(
            title = stringResource(R.string.set_custom_bg_color)
        ) {
            navigator.push(Route.ColorPicker(""))
        }
    }
    AnimatedVisibility(
        visible = changeBGColorType.value != ChangeBGColorTypes.NotChangeBGColor.ordinal,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            // 跳过已主动设置背景颜色的应用
            SwitchPreference(
                title = stringResource(R.string.skip_app_with_bg_color),
                key = Preferences.Background.SKIP_APP_WITH_BG_COLOR
            )
            // 配置应用列表
            TextPreference(title = stringResource(R.string.change_bg_color_list)) {
                navigator.push(Route.BackgroundExcept)
            }
        }
    }
    // 单独配置应用背景颜色
    TextPreference(title = stringResource(R.string.configure_bg_colors_individually)) {
        navigator.push(Route.BgIndividual)
    }
}

/**
 * 仅在 HyperOS 上生效的设置项
 */
@Composable
private fun MIUISettingsGroup(ignoreDarkMode: MutableState<Boolean>) {
    // 忽略深色模式
    SwitchPreference(
        title = stringResource(R.string.ignore_dark_mode),
        summary = stringResource(R.string.ignore_dark_mode_tips),
        checked = ignoreDarkMode
    )

    if ("android.app.TaskSnapshotHelperImpl".toClassOrNull() != null) {
        // 移除截图背景
        SwitchPreference(
            title = stringResource(R.string.remove_bg_drawable),
            summary = stringResource(R.string.remove_bg_drawable_tips),
            key = Preferences.Background.REMOVE_BG_DRAWABLE
        )
    }
}
