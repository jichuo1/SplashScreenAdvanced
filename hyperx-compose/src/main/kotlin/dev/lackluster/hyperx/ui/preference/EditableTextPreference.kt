package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.IntegratedTextField
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

@Composable
fun EditableTextPreference(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    icon: ImageIcon? = null,
    summary: String? = null,
    hint: String = "",
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
            IntegratedTextField(
                modifier = Modifier.widthIn(max = 160.dp),
                text = text,
                onTextChange = { if (enabled) onTextChange(it) },
                hint = hint,
                enabled = enabled
            )
        },
        enabled = enabled
    )
}

@Composable
fun EditableTextPreference(
    key: PreferenceKey<String>,
    title: String,
    icon: ImageIcon? = null,
    summary: String? = null,
    hint: String = "",
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onTextChange: (String) -> Unit,
) {
    val savedValue = rememberPreferenceState(key)

    EditableTextPreference(
        title = title,
        text = savedValue.value,
        onTextChange = { newValue ->
            savedValue.value = newValue
            onTextChange(newValue)
        },
        icon = icon,
        summary = summary,
        hint = hint,
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor
    )
}