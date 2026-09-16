package com.SplashScreenAdvanced.xposedmodule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalContext
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.LocalAppUiState
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.ui.preference.DropDownEntry
import dev.lackluster.hyperx.ui.preference.core.LocalPreferenceActions
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.DropDownPreference as HyperXDropDownPreference

/**
 * 下拉框可组合函数, 通过 [rememberPreferenceState] 管理远程 SharedPreferences, 并在模块未激活时提示用户。
 *
 * 注意：[entries] 中每项的 [DropDownEntry.value] 即为该项的下标（与旧的 index 语义一致）。
 * 可用 `entries.mapIndexed { index, title -> DropDownEntry(value = index, title = title) }` 构造。
 *
 * 只传 [key] 时直接绑定偏好状态, 避免再 remember 一份本地下标导致导入/重载后 UI 停在旧值。
 */
@Composable
fun DropDownPreference(
    title: String,
    summary: String? = null,
    entries: List<DropDownEntry<Int>>,
    key: PreferenceKey<Int>? = null,
    selectedIndex: MutableState<Int>? = null,
    showValue: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState = LocalAppUiState.current
    val actions = LocalPreferenceActions.current
    val boundState = if (selectedIndex == null) key?.let { rememberPreferenceState(it) } else null
    val current = selectedIndex ?: boundState
    val maxIndex = (entries.size - 1).coerceAtLeast(0)
    val value = (current?.value ?: 0).coerceIn(0, maxIndex)

    HyperXDropDownPreference(
        title = title,
        summary = summary,
        value = value,
        entries = entries,
        showValue = showValue,
        onValueChange = { newValue ->
            if (!uiState.moduleActive) {
                context.toast(R.string.make_sure_active)
            } else {
                current?.value = newValue
                if (selectedIndex != null && key != null) {
                    actions.update(key, newValue)
                }
                onSelectedIndexChange?.invoke(newValue)
            }
        },
    )
}
