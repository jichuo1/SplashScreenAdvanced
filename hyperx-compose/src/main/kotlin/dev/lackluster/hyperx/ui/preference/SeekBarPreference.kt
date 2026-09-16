package dev.lackluster.hyperx.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.lackluster.hyperx.R
import dev.lackluster.hyperx.core.utils.toDecimalString
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.dialog.EditTextDialog
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Slider
import kotlin.math.roundToInt

@Composable
fun SeekBarPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    defaultValue: Float = 0.0f,
    min: Float = 0f,
    max: Float = 100f,
    steps: Int = 0,
    showValue: Boolean = true,
    icon: ImageIcon? = null,
    summary: String? = null,
    valueFormatter: (Float) -> String = { it.toString() },
    dialogTitle: String? = null,
    dialogMessage: String? = null,
    dialogHint: String? = null,
    dialogInputType: EditTextInputType = EditTextInputType.Decimal,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
) {
    val showDialog = remember { mutableStateOf(false) }
    val holdDown = remember { mutableStateOf(false) }
    val formattedValue = valueFormatter(value)
    val rawInitialText = remember(value, dialogInputType) {
        if (dialogInputType == EditTextInputType.Number) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
    val hintText = remember(defaultValue, dialogHint, dialogInputType) {
        dialogHint
            ?: if (dialogInputType == EditTextInputType.Number) {
                defaultValue.toLong().toString()
            } else {
                defaultValue.toString()
            }
    }

    TextPreference(
        title = title,
        icon = icon,
        summary = summary,
        value = if (showValue) formattedValue else null,
        enabled = enabled,
        holdDownState = holdDown.value,
        titleColor = titleColor,
        summaryColor = summaryColor,
        bottomAction = {
            Slider(
                value = value,
                onValueChange = { if (enabled) onValueChange(it) },
                onValueChangeFinished = { if (enabled) onValueChangeFinished?.invoke() },
                valueRange = min..max,
                steps = steps,
                enabled = enabled
            )
        },
        onClick = {
            if (enabled) {
                showDialog.value = true
                holdDown.value = true
            }
        }
    )

    val keyboardType = if (dialogInputType == EditTextInputType.Number) {
        KeyboardType.Number
    } else {
        KeyboardType.Decimal
    }

    EditTextDialog(
        visible = showDialog.value,
        title = dialogTitle ?: title,
        onDismissRequest = { showDialog.value = false },
        onDismissFinished = { holdDown.value = false },
        message = dialogMessage,
        initialText = rawInitialText,
        hint = hintText,
        keyboardType = keyboardType,
        onConfirm = { newText ->
            val parsedValue = newText.toFloatOrNull() ?: value
            val clampedValue = parsedValue.coerceIn(min, max)
            onValueChange(clampedValue)
            onValueChangeFinished?.invoke()
        }
    )
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> SeekBarPreference(
    key: PreferenceKey<T>,
    title: String,
    min: T,
    max: T,
    steps: Int = 0,
    icon: ImageIcon? = null,
    summary: String? = null,
    valueFormatter: ((T) -> String)? = null,
    dialogTitle: String? = null,
    dialogMessage: String? = null,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onValueChangeFinished: ((T) -> Unit)? = null
) {
    val savedState = rememberPreferenceState(key)

    val currentFloatValue = (savedState.value as? Number)?.toFloat() ?: 0f
    val minFloat = (min as? Number)?.toFloat() ?: 0f
    val maxFloat = (max as? Number)?.toFloat() ?: 100f

    val sliderState = remember(currentFloatValue) {
        mutableFloatStateOf(currentFloatValue)
    }

    val autoInputType = remember(key.default) {
        when (key.default) {
            is Int, is Long -> EditTextInputType.Number
            is Float, is Double -> EditTextInputType.Decimal
            else -> EditTextInputType.Number
        }
    }

    val floatToT: (Float) -> T = remember(key.default) {
        { floatValue ->
            when (key.default) {
                is Int -> floatValue.roundToInt() as T
                is Float -> floatValue as T
                is Long -> floatValue.toLong() as T
                else -> floatValue as T
            }
        }
    }

    val smartFormatter: (Float) -> String = remember(key.default, valueFormatter) {
        { floatValue ->
            val typedValue = floatToT(floatValue)

            valueFormatter?.invoke(typedValue)
                ?:
                when (key.default) {
                    is Int -> typedValue.toString()
                    is Float -> (typedValue as Float).toDecimalString()
                    else -> typedValue.toString()
                }
        }
    }

    val defaultValueF = remember(key.default) {
        when (val def = key.default) {
            is Int, is Long, is Double -> def.toFloat()
            is Float -> def
            else -> 0.0f
        }
    }

    val actualDialogMessage = dialogMessage ?: when (key.default) {
        is Int, is Long -> stringResource(
            id = R.string.slider_dialog_message_decimal,
            key.default, min, max
        )
        is Float, is Double -> stringResource(
            id = R.string.slider_dialog_message_float,
            key.default, min, max
        )
        else -> null
    }

    SeekBarPreference(
        title = title,
        value = sliderState.floatValue,
        onValueChange = { newValue ->
            sliderState.floatValue = newValue
        },
        onValueChangeFinished = {
            val finalValue = floatToT(sliderState.floatValue)
            savedState.value = finalValue
            onValueChangeFinished?.invoke(finalValue)
        },
        defaultValue = defaultValueF,
        min = minFloat,
        max = maxFloat,
        steps = steps,
        icon = icon,
        summary = summary,
        valueFormatter = smartFormatter,
        dialogTitle = dialogTitle,
        dialogMessage = actualDialogMessage,
        dialogInputType = autoInputType,
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor
    )
}