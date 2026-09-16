package com.SplashScreenAdvanced.xposedmodule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.LocalAppUiState
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.preference.core.LocalPreferenceActions
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.SwitchPreference as HyperXSwitchPreference

/**
 * 开关可组合函数, 通过 [rememberPreferenceState] 管理远程 SharedPreferences, 并在模块未激活时提示用户
 */
@Composable
fun SwitchPreference(
    icon: ImageIcon? = null,
    title: String,
    summary: String? = null,
    key: PreferenceKey<Boolean>? = null,
    enabled: Boolean = true,
    checked: MutableState<Boolean>? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState = LocalAppUiState.current
    val actions = LocalPreferenceActions.current
    val boundState = if (checked == null) key?.let { rememberPreferenceState(it) } else null
    val currentChecked = checked
        ?: boundState
        ?: remember { mutableStateOf(false) }

    HyperXSwitchPreference(
        title = title,
        summary = summary,
        icon = icon,
        checked = currentChecked.value,
        enabled = enabled,
        onCheckedChange = { newValue ->
            if (!uiState.moduleActive) {
                context.toast(R.string.make_sure_active)
            } else {
                currentChecked.value = newValue
                if (checked != null && key != null) {
                    actions.update(key, newValue)
                }
                onCheckedChange?.invoke(newValue)
            }
        },
    )
}
