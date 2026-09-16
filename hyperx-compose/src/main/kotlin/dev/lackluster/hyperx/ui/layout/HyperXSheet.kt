package dev.lackluster.hyperx.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.icons.ImmersionClose
import dev.lackluster.hyperx.ui.icons.ImmersionConfirm
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HyperXSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    allowDismiss: Boolean = true,
    onNegativeButton: ((dismiss: () -> Unit) -> Unit)? = { dismiss -> dismiss() },
    onPositiveButton: ((dismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val insetsPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues()

    OverlayBottomSheet(
        show = show,
        title = title,
        allowDismiss = allowDismiss,
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize.Zero,
        startAction = {
            val dismissState = LocalDismissState.current
            val dismissAction: () -> Unit = { dismissState?.invoke() }
            if (onNegativeButton != null) {
                IconButton(
                    modifier = Modifier.padding(start = 16.dp).size(40.dp),
                    onClick = { onNegativeButton(dismissAction) }
                ) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = MiuixIcons.ImmersionClose,
                        contentDescription = "Close",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        },
        endAction = {
            val dismissState = LocalDismissState.current
            val dismissAction: () -> Unit = { dismissState?.invoke() }

            if (onPositiveButton != null) {
                IconButton(
                    modifier = Modifier.padding(end = 16.dp).size(40.dp),
                    onClick = { onPositiveButton(dismissAction) }
                ) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = MiuixIcons.ImmersionConfirm,
                        contentDescription = "Confirm",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = insetsPadding.calculateBottomPadding()),
            content = content
        )
    }
}