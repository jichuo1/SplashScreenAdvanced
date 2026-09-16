package dev.lackluster.hyperx.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults

@Composable
fun CheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageIcon? = null,
    summary: String? = null,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
) {
    BasicComponent(
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = icon?.let { imageIcon ->
            { PreferenceIconSlot(icon = imageIcon) }
        },
        endActions = {
            Checkbox(
                state = ToggleableState(checked),
                onClick = null,
                modifier = Modifier,
                colors = CheckboxDefaults.checkboxColors(),
                enabled = enabled
            )
        },
        onClick = {
            if (enabled) onCheckedChange(!checked)
        },
        enabled = enabled
    )
}

@Composable
fun CheckboxPreference(
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

    CheckboxPreference(
        title = title,
        checked = savedValue.value,
        onCheckedChange = { newValue ->
            savedValue.value = newValue
            onCheckedChange(newValue)
        },
        icon = icon,
        summary = summary,
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor
    )
}