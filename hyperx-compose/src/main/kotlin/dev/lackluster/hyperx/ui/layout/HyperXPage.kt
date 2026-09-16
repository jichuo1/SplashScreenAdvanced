package dev.lackluster.hyperx.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.navigation.LocalNavigator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HyperXPage(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    navigationIcon: @Composable () -> Unit = {
        val navigator = LocalNavigator.current
        IconButton(
            modifier = Modifier
                .padding(start = 21.dp)
                .size(40.dp),
            onClick = { navigator.pop() }
        ) {
            Icon(
                modifier = Modifier.size(26.dp),
                imageVector = MiuixIcons.Back,
                contentDescription = "Back",
                tint = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    },
    actions: @Composable RowScope.() -> Unit = {},
    fixedHeader: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val uiConfig = LocalHyperXLayoutConfig.current
    val layoutPadding = LocalLayoutPadding.current

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val layoutDirection = LocalLayoutDirection.current


    val containerColor = MiuixTheme.colorScheme.surface
    val topBarColor = if (uiConfig.isBlurEnabled) {
        Color.Transparent
    } else {
        containerColor
    }

    val blurTintAlpha = if (containerColor.luminance() >= 0.5f) {
        uiConfig.lightBlurAlpha
    } else {
        uiConfig.darkBlurAlpha
    }

    HyperXScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { contentPadding ->
            TopAppBar(
                color = topBarColor,
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { navigationIcon() },
                actions = { actions(this) },
                defaultWindowInsetsPadding = false,
                titlePadding = 28.dp + contentPadding.calculateStartPadding(layoutDirection),
                navigationIconPadding = contentPadding.calculateStartPadding(layoutDirection),
                actionIconPadding = contentPadding.calculateEndPadding(layoutDirection),
                bottomContent = {
                    fixedHeader?.let {
                        Box(
                            modifier = Modifier
                                .background(topBarColor)
                                .padding(contentPadding)
                        ) {
                            it()
                        }
                    }
                }
            )
        },
        blurTopBar = uiConfig.isBlurEnabled,
        containerColor = containerColor,
        blurTintAlpha = blurTintAlpha,
        layoutPadding = layoutPadding,
    ) { paddingValues ->
        LazyColumn(
            modifier = contentModifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = paddingValues,
            content = content,
        )
    }
}