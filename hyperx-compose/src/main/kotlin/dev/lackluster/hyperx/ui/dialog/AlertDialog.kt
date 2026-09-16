package dev.lackluster.hyperx.ui.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun AlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String?,
    message: String? = null,
    cancelable: Boolean = true,
    mode: AlertDialogMode = AlertDialogMode.Positive,
    negativeText: String = stringResource(R.string.button_cancel),
    positiveText: String = stringResource(R.string.button_ok),
    onNegativeButton: (() -> Unit)? = null,
    onPositiveButton: (() -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current

    OverlayDialog(
        show = visible,
        title = title,
        summary = message,
        onDismissRequest = {
            if (cancelable) onDismissRequest()
        },
        content = {
            BackHandler(enabled = !cancelable) {}
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mode != AlertDialogMode.Positive) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = negativeText,
                        minHeight = 50.dp,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (onNegativeButton != null) onNegativeButton() else onDismissRequest()
                        }
                    )
                }

                if (mode != AlertDialogMode.Negative) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = positiveText,
                        minHeight = 50.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (onPositiveButton != null) onPositiveButton() else onDismissRequest()
                        }
                    )
                }
            }
        }
    )
}

@Composable
fun AlertDialog(
    visibility: MutableState<Boolean>,
    title: String?,
    message: String? = null,
    cancelable: Boolean = true,
    mode: AlertDialogMode = AlertDialogMode.Positive,
    negativeText: String = stringResource(R.string.button_cancel),
    positiveText: String = stringResource(R.string.button_ok),
    onNegativeButton: (() -> Unit)? = null,
    onPositiveButton: (() -> Unit)? = null,
) {
    AlertDialog(
        visible = visibility.value,
        onDismissRequest = { visibility.value = false },
        title = title,
        message = message,
        cancelable = cancelable,
        mode = mode,
        negativeText = negativeText,
        positiveText = positiveText,
        onNegativeButton = onNegativeButton,
        onPositiveButton = onPositiveButton
    )
}

enum class AlertDialogMode {
    Negative,
    Positive,
    NegativeAndPositive,
}