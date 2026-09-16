package com.SplashScreenAdvanced.xposedmodule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.MainActivity
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.toast
import dev.lackluster.hyperx.ui.preference.DropDownEntry
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.DropDownPreference as HyperXDropDownPreference

/**
 * 下拉框可组合函数, 通过 [rememberPreferenceState] 管理远程 SharedPreferences, 并在模块未激活时提示用户。
 *
 * 注意：[entries] 中每项的 [DropDownEntry.value] 即为该项的下标（与旧的 index 语义一致）。
 * 可用 `entries.mapIndexed { index, title -> DropDownEntry(value = index, title = title) }` 构造。
 */
@Composable
fun DropDownPreference(
    title: String,
    summary: String? = null,
    entries: List<DropDownEntry<Int>>,
    key: PreferenceKey<Int>? = null,
    selectedIndex: MutableIntState? = null,
    showValue: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val boundState = key?.let { rememberPreferenceState(it) }

    val currentSelectedIndex = selectedIndex
        ?: remember(boundState?.value, entries.size) {
            mutableIntStateOf((boundState?.value ?: 0).coerceIn(0, entries.size - 1))
        }

    HyperXDropDownPreference(
        title = title,
        summary = summary,
        value = currentSelectedIndex.intValue,
        entries = entries,
        showValue = showValue,
        onValueChange = { newValue ->
            if (!MainActivity.moduleActive.value) {
                context.toast(R.string.make_sure_active)
            } else {
                boundState?.value = newValue
                currentSelectedIndex.intValue = newValue
                onSelectedIndexChange?.invoke(newValue)
            }
        },
    )
}
