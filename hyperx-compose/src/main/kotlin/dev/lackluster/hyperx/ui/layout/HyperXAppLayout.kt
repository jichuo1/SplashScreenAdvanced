package dev.lackluster.hyperx.ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.lackluster.hyperx.R
import dev.lackluster.hyperx.navigation.HyperXRoute
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
import dev.lackluster.hyperx.ui.animation.HyperXNavTransitions
import dev.lackluster.hyperx.ui.component.AdaptiveIcon
import dev.lackluster.hyperx.ui.component.IconSize
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.ImageSource
import dev.lackluster.hyperx.ui.theme.HyperXTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

@Composable
fun HyperXAppLayout(
    config: HyperXLayoutConfig = HyperXLayoutConfig(),
    customEntryProvider: ((key: NavKey) -> NavEntry<NavKey>)? = null,
    emptyContent: @Composable () -> Unit = { DefaultEmptyPage() },
    primaryContent: @Composable () -> Unit
) {
    HyperXTheme {
        val density = LocalDensity.current
        val containerSize = LocalWindowInfo.current.containerSize
        val windowWidth = with(density) { containerSize.width.toDp() }
        val windowHeight = with(density) { containerSize.height.toDp() }

        val isLandscape = windowWidth > windowHeight
        val largeScreen = windowHeight >= 480.dp && windowWidth >= 840.dp

        val backStack = rememberNavBackStack(HyperXRoute.Main)
        val navigator = remember(backStack) { Navigator(backStack) }

        val appRootLayout = when {
            config.isSplitScreenEnabled && largeScreen && isLandscape -> AppRootLayout.Split12
            config.isSplitScreenEnabled && (largeScreen || isLandscape) -> AppRootLayout.Split11
            largeScreen -> AppRootLayout.LargeScreen
            else -> AppRootLayout.Normal
        }

        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalHyperXLayoutConfig provides config
        ) {
            AnimatedContent(
                targetState = appRootLayout,
                label = "HyperXLayoutSwitch",
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                }
            ) { targetLayout ->
                when (targetLayout) {
                    AppRootLayout.Split11, AppRootLayout.Split12 -> {
                        val rightWeight = if (targetLayout == AppRootLayout.Split12) 2.0f else 1.0f
                        UnifiedSplitLayout(
                            backStack = backStack,
                            rightWeight = rightWeight,
                            primaryContent = primaryContent,
                            emptyContent = emptyContent,
                            customEntryProvider = customEntryProvider
                        )
                    }
                    else -> {
                        val extraPadding = if (targetLayout == AppRootLayout.LargeScreen) {
                            PaddingValues(horizontal = windowWidth * 0.1f)
                        } else {
                            PaddingValues(0.dp)
                        }
                        UnifiedNormalLayout(
                            backStack = backStack,
                            extraPadding = extraPadding,
                            primaryContent = primaryContent,
                            customEntryProvider = customEntryProvider
                        )
                    }
                }
            }
        }
        MiuixPopupUtils.MiuixPopupHost()
    }
}

@Composable
private fun UnifiedNormalLayout(
    backStack: MutableList<NavKey>,
    extraPadding: PaddingValues,
    primaryContent: @Composable () -> Unit,
    customEntryProvider: ((key: NavKey) -> NavEntry<NavKey>)?
) {
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal).asPaddingValues()

    val contentPadding = PaddingValues(
        start = systemBarInsets.calculateStartPadding(layoutDirection) + extraPadding.calculateStartPadding(layoutDirection),
        top = extraPadding.calculateTopPadding(),
        end = systemBarInsets.calculateEndPadding(layoutDirection) + extraPadding.calculateEndPadding(layoutDirection),
        bottom = extraPadding.calculateBottomPadding()
    )

    // 局部下发：全屏模式和当前页面的 Padding
    CompositionLocalProvider(
        LocalPageMode provides PageLayoutMode.FULL_SCREEN,
        LocalLayoutPadding provides contentPadding
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            transitionSpec = HyperXNavTransitions.normalTransitionSpec(layoutDirection),
            popTransitionSpec = HyperXNavTransitions.normalPopTransitionSpec(layoutDirection),
            predictivePopTransitionSpec = HyperXNavTransitions.normalPredictivePopTransitionSpec(layoutDirection),
            transitionEffects = HyperXNavTransitions.NormalTransitionEffects,
            entryProvider = { key ->
                when (key) {
                    // 全屏下，遇到 Main 路由直接渲染主页
                    is HyperXRoute.Main -> NavEntry(key) { primaryContent() }
                    else -> customEntryProvider?.invoke(key) ?: NavEntry(key) {}
                }
            }
        )
    }
}

@Composable
private fun UnifiedSplitLayout(
    backStack: MutableList<NavKey>,
    rightWeight: Float,
    primaryContent: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    customEntryProvider: ((key: NavKey) -> NavEntry<NavKey>)?
) {
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal).asPaddingValues()

    val primaryPadding = PaddingValues(
        start = systemBarInsets.calculateStartPadding(layoutDirection) + 12.dp,
        top = systemBarInsets.calculateTopPadding(),
        end = 12.dp,
        bottom = systemBarInsets.calculateBottomPadding()
    )

    val secondaryPadding = PaddingValues(
        start = 12.dp,
        top = systemBarInsets.calculateTopPadding(),
        end = systemBarInsets.calculateEndPadding(layoutDirection) + 12.dp,
        bottom = systemBarInsets.calculateBottomPadding()
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(
                LocalPageMode provides PageLayoutMode.SPLIT_PRIMARY,
                LocalLayoutPadding provides primaryPadding
            ) {
                primaryContent()
            }
        }

        VerticalDivider(thickness = 0.75.dp, color = MiuixTheme.colorScheme.dividerLine)

        // 右半边区域：根据路由栈渲染详情页
        CompositionLocalProvider(
            LocalPageMode provides PageLayoutMode.SPLIT_SECONDARY,
            LocalLayoutPadding provides secondaryPadding
        ) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.weight(rightWeight),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                transitionSpec = HyperXNavTransitions.splitTransitionSpec(layoutDirection),
                popTransitionSpec = HyperXNavTransitions.splitTransitionSpec(layoutDirection),
                predictivePopTransitionSpec = HyperXNavTransitions.splitPredictivePopTransitionSpec(layoutDirection),
                transitionEffects = HyperXNavTransitions.SplitTransitionEffects,
                entryProvider = { key ->
                    when (key) {
                        is HyperXRoute.Main -> NavEntry(key) { emptyContent() }
                        else -> customEntryProvider?.invoke(key) ?: NavEntry(key) {}
                    }
                }
            )
        }
    }
}

@Composable
fun DefaultEmptyPage(
    imageIcon: ImageIcon = ImageIcon(
        source = ImageSource.Res(R.drawable.ic_miuix),
        size = IconSize.Unspecified,
        customSizeDp = 255.dp
    )
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveIcon(imageIcon)
    }
}

enum class AppRootLayout {
    Normal,
    LargeScreen,
    Split11,
    Split12
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp,
    color: Color,
) =
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness)
    ) {
        drawLine(
            color = color,
            strokeWidth = thickness.toPx(),
            start = Offset(thickness.toPx() / 2, 0f),
            end = Offset(thickness.toPx() / 2, size.height),
        )
    }