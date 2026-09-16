package dev.lackluster.hyperx.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.dialog.EditTextDialog
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

enum class EditTextInputType {
    Text,     // 普通文本 -> 存为 String
    Number,   // 纯数字键盘 -> 存为 Int
    Decimal,  // 带小数点的数字键盘 -> 存为 Float
    Password, // 全键盘 + 星号掩码 -> 存为 String
}

enum class ValuePosition {
    Hidden,
    Value,
    Summary,
}

@Composable
fun EditTextPreference(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    icon: ImageIcon? = null,
    summary: String? = null,
    inputType: EditTextInputType = EditTextInputType.Text,
    valuePosition: ValuePosition = ValuePosition.Value,
    dialogTitle: String? = null,
    dialogMessage: String? = null,
    dialogHint: String = "",
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
) {
    val showDialog = remember { mutableStateOf(false) }
    val holdDown = remember { mutableStateOf(false) }

    val displayValue = if (inputType == EditTextInputType.Password) "******" else text.takeIf { it.isNotBlank() }
    val actualSummary = when (valuePosition) {
        ValuePosition.Summary -> {
            if (displayValue == null) {
                summary
            } else if (summary.isNullOrEmpty()) {
                displayValue
            } else {
                "$summary\n$displayValue"
            }
        }
        else -> summary
    }
    val actualRightValue = when (valuePosition) {
        ValuePosition.Value -> displayValue
        else -> null
    }

    TextPreference(
        title = title,
        icon = icon,
        summary = actualSummary,
        value = actualRightValue,
        enabled = enabled,
        holdDownState = holdDown.value,
        titleColor = titleColor,
        summaryColor = summaryColor,
        onClick = {
            if (enabled) {
                showDialog.value = true
                holdDown.value = true
            }
        }
    )

    val keyboardType = when (inputType) {
        EditTextInputType.Number -> KeyboardType.Number
        EditTextInputType.Decimal -> KeyboardType.Decimal
        EditTextInputType.Password -> KeyboardType.Password
        else -> KeyboardType.Text
    }

    val visualTransformation = if (inputType == EditTextInputType.Password) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    EditTextDialog(
        visible = showDialog.value,
        title = dialogTitle ?: title,
        onDismissRequest = { showDialog.value = false },
        onDismissFinished = { holdDown.value = false },
        message = dialogMessage,
        initialText = text,
        hint = dialogHint,
        keyboardType = keyboardType,
        visualTransformation = visualTransformation,
        onConfirm = { newText ->
            onTextChange(newText)
        }
    )
}

@Composable
fun <T: Any> EditTextPreference(
    key: PreferenceKey<T>,
    title: String,
    icon: ImageIcon? = null,
    summary: String? = null,
    isValueValid: (T) -> Boolean = { true },
    inputType: EditTextInputType? = null,
    valuePosition: ValuePosition = ValuePosition.Value,
    dialogTitle: String? = title,
    dialogMessage: String? = null,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onValueChange: (T) -> Unit = {},
) {
    val savedValue = rememberPreferenceState(key)

    val autoInputType = remember(key.default, inputType) {
        inputType ?: when (key.default) {
            is Int, is Long -> EditTextInputType.Number
            is Float, is Double -> EditTextInputType.Decimal
            is String -> EditTextInputType.Text
            else -> EditTextInputType.Text
        }
    }

    EditTextPreference(
        title = title,
        text = savedValue.value.toString(),
        onTextChange = {
            parseStringAsT(it, key.default)?.let { parsedValue ->
                if (isValueValid(parsedValue)) {
                    savedValue.value = parsedValue
                    onValueChange(parsedValue)
                }
            }
        },
        icon = icon,
        summary = summary,
        inputType = autoInputType,
        valuePosition = valuePosition,
        dialogTitle = dialogTitle,
        dialogMessage = dialogMessage,
        dialogHint = key.default.toString(),
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor,
    )
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> parseStringAsT(input: String, defaultValue: T): T? {
    return try {
        when (defaultValue) {
            is String -> input as T
            is Int -> input.toInt() as T
            is Float -> input.toFloat() as T
            is Long -> input.toLong() as T
            is Boolean -> input.toBooleanStrict() as T
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}