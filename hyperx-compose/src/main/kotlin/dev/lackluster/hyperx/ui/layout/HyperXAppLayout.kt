package dev.lackluster.hyperx.ui.layout

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
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.lackluster.hyperx.ui.animation.LocalPageMotion
import dev.lackluster.hyperx.ui.animation.MotionPageScope
import dev.lackluster.hyperx.ui.animation.PageMotionController
import dev.lackluster.hyperx.ui.animation.PageMotionHost
import dev.lackluster.hyperx.ui.animation.PageMotionEntry
import dev.lackluster.hyperx.ui.animation.pageMotionInput
import dev.lackluster.hyperx.ui.animation.rememberPageMotionController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.lackluster.hyperx.R
import dev.lackluster.hyperx.navigation.HyperXRoute
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
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
    startBackStack: List<NavKey> = listOf(HyperXRoute.Main),
    onDestinationChanged: (NavKey?) -> Unit = {},
    primaryContent: @Composable () -> Unit
) {
    HyperXTheme(uiStyle = config.uiStyle) {
        val density = LocalDensity.current
        val containerSize = LocalWindowInfo.current.containerSize
        val windowWidth = with(density) { containerSize.width.toDp() }
        val windowHeight = with(density) { containerSize.height.toDp() }

        val isLandscape = windowWidth > windowHeight
        val largeScreen = windowHeight >= 480.dp && windowWidth >= 840.dp

        val initialKeys = remember {
            startBackStack.ifEmpty { listOf(HyperXRoute.Main) }.toTypedArray()
        }
        val backStack = rememberNavBackStack(*initialKeys)
        val currentKey = backStack.lastOrNull()
        val motion = rememberPageMotionController { closingKey ->
            if (backStack.size > 1 && backStack.last() == closingKey) backStack.removeLastOrNull()
        }
        SideEffect {
            motion.syncBackStack(backStack.toList())
            onDestinationChanged(currentKey)
        }
        val navigator = remember(backStack, motion) { Navigator(backStack, motion) }

        val appRootLayout = when {
            config.isSplitScreenEnabled && largeScreen && isLandscape -> AppRootLayout.Split12
            config.isSplitScreenEnabled && (largeScreen || isLandscape) -> AppRootLayout.Split11
            largeScreen -> AppRootLayout.LargeScreen
            else -> AppRootLayout.Normal
        }

        val split = appRootLayout == AppRootLayout.Split11 || appRootLayout == AppRootLayout.Split12
        val entryProvider = remember<(NavKey) -> NavEntry<NavKey>>(primaryContent, emptyContent, customEntryProvider, split) {
            { navKey: NavKey ->
                when (navKey) {
                    is HyperXRoute.Main -> NavEntry<NavKey>(navKey) { if (split) emptyContent() else primaryContent() }
                    else -> customEntryProvider?.invoke(navKey) ?: NavEntry(navKey) {}
                }
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        ).mapIndexed { index, entry -> PageMotionEntry(backStack[index], entry) }

        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalHyperXLayoutConfig provides config,
            LocalUiStyle provides config.uiStyle,
            LocalPageMotion provides motion,
        ) {
            // A layout-mode change settles the old motion host before measuring the new pane.
            key(appRootLayout) {
                val targetLayout = appRootLayout
                when (targetLayout) {
                    AppRootLayout.Split11, AppRootLayout.Split12 -> {
                        val rightWeight = if (targetLayout == AppRootLayout.Split12) 2.0f else 1.0f
                        UnifiedSplitLayout(
                            entries = entries,
                            motion = motion,
                            rightWeight = rightWeight,
                            primaryContent = primaryContent,
                        )
                    }
                    else -> {
                        val extraPadding = if (targetLayout == AppRootLayout.LargeScreen) {
                            PaddingValues(horizontal = windowWidth * 0.1f)
                        } else {
                            PaddingValues(0.dp)
                        }
                        UnifiedNormalLayout(
                            entries = entries,
                            motion = motion,
                            extraPadding = extraPadding,
                        )
                    }
                }
            }
            MiuixPopupUtils.MiuixPopupHost()
        }
    }
}

@Composable
private fun UnifiedNormalLayout(
    entries: List<PageMotionEntry>,
    motion: PageMotionController,
    extraPadding: PaddingValues,
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
        PageMotionHost(motion, entries)
    }
}

@Composable
private fun UnifiedSplitLayout(
    entries: List<PageMotionEntry>,
    motion: PageMotionController,
    rightWeight: Float,
    primaryContent: @Composable () -> Unit,
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
        Box(modifier = Modifier.weight(1f).pageMotionInput(motion, active = true)
            .then(if (motion.inputBlocked) Modifier.clearAndSetSemantics {} else Modifier)) {
            CompositionLocalProvider(
                LocalPageMode provides PageLayoutMode.SPLIT_PRIMARY,
                LocalLayoutPadding provides primaryPadding
            ) {
                MotionPageScope(HyperXRoute.Main, active = true) { primaryContent() }
            }
        }

        VerticalDivider(thickness = 0.75.dp, color = MiuixTheme.colorScheme.dividerLine)

        // 右半边区域：根据路由栈渲染详情页
        CompositionLocalProvider(
            LocalPageMode provides PageLayoutMode.SPLIT_SECONDARY,
            LocalLayoutPadding provides secondaryPadding
        ) {
            PageMotionHost(motion, entries, Modifier.weight(rightWeight))
        }
    }
}

@Composable
fun DefaultEmptyPage(
    imageIcon: ImageIcon? = null
) {
    val icon = imageIcon ?: remember {
        ImageIcon(
            source = ImageSource.Res(R.drawable.ic_miuix),
            size = IconSize.Unspecified,
            customSizeDp = 255.dp
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AdaptiveIcon(icon)
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
