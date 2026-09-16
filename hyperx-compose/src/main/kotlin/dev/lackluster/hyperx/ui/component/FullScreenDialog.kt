package dev.lackluster.hyperx.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.icons.ImmersionClose
import dev.lackluster.hyperx.ui.icons.ImmersionConfirm
import dev.lackluster.hyperx.ui.layout.HyperXPage
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FullScreenDialog(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onNegativeButton: (() -> Unit)? = null,
    onPositiveButton: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val navigator = LocalNavigator.current

    val currentOnNegative by rememberUpdatedState(onNegativeButton)
    val currentOnPositive by rememberUpdatedState(onPositiveButton)

    val shouldInterceptBack = currentOnNegative != null

    BackHandler(enabled = shouldInterceptBack) {
        currentOnNegative?.invoke()
    }

    HyperXPage(
        title = title,
        modifier = modifier,
        contentModifier = contentModifier,
        listState = listState,
        navigationIcon = {
            IconButton(
                modifier = Modifier
                    .padding(start = 21.dp)
                    .size(40.dp),
                onClick = {
                    if (currentOnNegative != null) {
                        currentOnNegative?.invoke()
                    } else {
                        navigator.pop()
                    }
                }
            ) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    imageVector = MiuixIcons.ImmersionClose,
                    contentDescription = "Close",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        },
        actions = {
            IconButton(
                modifier = Modifier
                    .padding(end = 21.dp)
                    .size(40.dp),
                onClick = {
                    if (currentOnPositive != null) {
                        currentOnPositive?.invoke()
                    } else {
                        navigator.pop()
                    }
                }
            ) {
                Icon(
                    modifier = Modifier.size(26.dp),
                    imageVector = MiuixIcons.ImmersionConfirm,
                    contentDescription = "Confirm",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        },
        content = content
    )
}