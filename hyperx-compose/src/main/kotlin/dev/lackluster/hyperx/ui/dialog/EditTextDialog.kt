package dev.lackluster.hyperx.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EditTextDialog(
    visible: Boolean,
    title: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    message: String? = null,
    initialText: String = "",
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    negativeText: String = stringResource(R.string.button_cancel),
    positiveText: String = stringResource(R.string.button_ok),
) {
    var inputText by remember(visible) {
        mutableStateOf(
            TextFieldValue(text = initialText, selection = TextRange(initialText.length))
        )
    }

    val focusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current

    OverlayDialog(
        show = visible,
        title = title,
        summary = message,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        content = {
            LaunchedEffect(visible) {
                if (visible) {
                    delay(100)
                    focusRequester.requestFocus()
                }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()), // 防止横屏崩溃，体验不好待改
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = hint,
                    useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onBackground),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = visualTransformation
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = negativeText,
                        minHeight = 50.dp,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismissRequest()
                        }
                    )

                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = positiveText,
                        minHeight = 50.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onConfirm(inputText.text)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    )
}