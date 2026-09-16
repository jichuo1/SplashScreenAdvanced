package com.SplashScreenAdvanced.xposedmodule.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.LocalAppUiState
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.EditTextInputType
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.SeekBarPreference
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * 开发者选项
 */
@Composable
fun DevPage() {
    val navigator = LocalNavigator.current
    val repo = koinInject<GlobalPreferencesRepository>()
    val uiState = LocalAppUiState.current
    HyperXPage(
        title = stringResource(R.string.dev_settings),
    ) {
        itemPreferenceGroup(key = "general", position = ItemPosition.First) {
            SwitchPreference(
                title = stringResource(R.string.dev_settings),
                key = Preferences.Dev.ENABLE_DEV_SETTINGS,
                onCheckedChange = {
                    uiState.syncDevMode()
                    navigator.pop()
                }
            )
        }
        itemPreferenceGroup(
            titleRes = R.string.icon_settings,
            position = ItemPosition.Last
        ) {
            val roundCornerRate = remember {
                mutableFloatStateOf(repo.get(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE).toFloat())
            }
            SeekBarPreference(
                title = stringResource(R.string.dev_icon_round_corner_rate),
                value = roundCornerRate.floatValue,
                onValueChange = { roundCornerRate.floatValue = it },
                onValueChangeFinished = {
                    repo.update(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE, roundCornerRate.floatValue.roundToInt())
                },
                defaultValue = Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE.default.toFloat(),
                min = 0f,
                max = 50f,
                dialogInputType = EditTextInputType.Number,
                valueFormatter = { "%d%% / 50%%".format(it.roundToInt()) }
            )
        }
    }
}
