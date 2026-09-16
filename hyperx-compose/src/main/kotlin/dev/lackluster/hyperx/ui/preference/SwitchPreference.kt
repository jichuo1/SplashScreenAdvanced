package dev.lackluster.hyperx.ui.preference

import androidx.compose.runtime.Composable
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageIcon? = null,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
) {
    SwitchPreference(
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = icon?.let { imageIcon ->
            { PreferenceIconSlot(icon = imageIcon) }
        },
        checked = checked,
        onCheckedChange = { if (enabled) onCheckedChange(it) },
        enabled = enabled
    )
}

@Composable
fun SwitchPreference(
    key: PreferenceKey<Boolean>,
    title: String,
    icon: ImageIcon? = null,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val savedValue = rememberPreferenceState(key)

    SwitchPreference(
        title = title,
        icon = icon,
        summary = summary,
        checked = savedValue.value,
        onCheckedChange = { newValue ->
            savedValue.value = newValue
            onCheckedChange(newValue)
        },
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor,
    )
}