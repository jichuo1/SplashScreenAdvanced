package dev.lackluster.hyperx.ui.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoadingDialog(
    visible: Boolean,
    title: String,
    summary: String? = null,
    cancelable: Boolean = false,
    onDismissRequest: () -> Unit = {}
) {
    OverlayDialog(
        show = visible,
        onDismissRequest = {
            if (cancelable) onDismissRequest()
        },
        content = {
            BackHandler(enabled = !cancelable) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfiniteProgressIndicator(
                    size = 26.dp,
                    strokeWidth = 2.5.dp,
                    orbitingDotSize = 2.5.dp,
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = title,
                        color = DialogDefaults.titleColor(),
                        fontWeight = FontWeight.Medium,
                        style = MiuixTheme.textStyles.title4
                    )

                    if (!summary.isNullOrEmpty()) {
                        Text(
                            text = summary,
                            color = DialogDefaults.summaryColor(),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun LoadingDialog(
    visibility: MutableState<Boolean>,
    title: String,
    summary: String? = null,
    cancelable: Boolean = false
) {
    LoadingDialog(
        visible = visibility.value,
        title = title,
        summary = summary,
        cancelable = cancelable,
        onDismissRequest = { visibility.value = false }
    )
}