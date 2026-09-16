package com.SplashScreenAdvanced.xposedmodule.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
 * 底部 界面
 */
@Composable
fun BottomPage() {
    val navigator = LocalNavigator.current
    HyperXPage(
        title = stringResource(R.string.bottom_settings),
    ) {
        item(key = "header") {
            HeaderCard(
                imageResID = R.drawable.demo_branding,
                title = "BRANDING\nIMAGE",
                maxLines = 2
            )
        }
        itemPreferenceGroup(key = "branding", position = ItemPosition.Last) {
            RemoveBrandingImageSettingsGroup(navigator)
        }
    }
}

/**
 * 移除底部图片
 */
@Composable
private fun RemoveBrandingImageSettingsGroup(navigator: Navigator) {
    val context = LocalContext.current
    val removeBrandingImage = rememberPreferenceState(Preferences.Display.REMOVE_BRANDING_IMAGE)

    SwitchPreference(
        title = stringResource(R.string.remove_branding_image),
        summary = stringResource(R.string.remove_branding_image_tips),
        checked = removeBrandingImage
    ) { newValue ->
        if (newValue) {
            context.toast(R.string.custom_scope_message)
        }
    }
    AnimatedVisibility(
        visible = removeBrandingImage.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        TextPreference(title = stringResource(R.string.remove_branding_image_list)) {
            navigator.push(Route.RemoveBranding)
        }
    }
}
