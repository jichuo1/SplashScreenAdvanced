package dev.lackluster.hyperx.ui.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * Real measured large and collapsed titles. Miuix still owns bar sizing, scrolling and actions;
 * its transparent titles reserve the original space and accessibility semantics.
 * Placement follows Miuix 0.9.3 TopAppBar (Apache-2.0), including the action/title width constraints.
 */
@Composable
fun PageMotionTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val motion = LocalPageMotion.current
    val owner = LocalPageMotionKey.current
    val density = LocalDensity.current
    var navigationWidth by remember { mutableIntStateOf(0) }
    var actionsWidth by remember { mutableIntStateOf(0) }
    val largeFont = MiuixTheme.textStyles.title1.fontSize
    val smallFont = MiuixTheme.textStyles.title3.fontSize
    fun largeAlpha() = 1f - ((scrollBehavior?.state?.collapsedFraction ?: 0f) * 3f).coerceIn(0f, 1f)

    Box(modifier.clipToBounds()) {
        TopAppBar(
            title = title, color = color, titleColor = Color.Transparent,
            largeTitleColor = Color.Transparent,
            navigationIcon = {
                Box(Modifier.onSizeChanged { navigationWidth = it.width }.onGloballyPositioned {
                    if (owner != null) motion?.reportBackTarget(owner, it.unclippedBoundsInWindow())
                }) { navigationIcon() }
            },
            actions = {
                Row(Modifier.onSizeChanged { actionsWidth = it.width }, content = actions)
            },
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding, navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding, bottomContent = bottomContent,
        )
        Layout(
            modifier = Modifier.matchParentSize()
                .then(if (defaultWindowInsetsPadding) Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)) else Modifier)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            content = {
                for (large in listOf(true, false)) {
                    val measurement = remember { TitleMeasurement() }
                    val fontSize = if (large) largeFont else smallFont
                    val weight = if (large) FontWeight.Normal else FontWeight.Medium
                    Text(
                        text = title, fontSize = fontSize, fontWeight = weight, color = titleColor,
                        softWrap = large, maxLines = if (large) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { measurement.singleLine = it.lineCount == 1 && !it.hasVisualOverflow },
                        modifier = Modifier
                            .onGloballyPositioned {
                                if (owner != null && (largeAlpha() >= 0.5f) == large) {
                                    motion?.reportTarget(PageMotionAnchor(
                                        title, it.unclippedBoundsInWindow(), it.unclippedBoundsInWindow(),
                                        with(density) { fontSize.toPx() }, titleColor, weight,
                                        owner, if (large) "large-title" else "small-title", measurement.singleLine,
                                    ))
                                }
                            }
                            .clearAndSetSemantics {}
                            .graphicsLayer {
                                alpha = if (motion?.isHidingTargetTitle(owner) == true) 0f
                                    else if (large) largeAlpha() else 1f - largeAlpha()
                            },
                    )
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val padding = titlePadding.roundToPx()
            val navWidth = navigationWidth + navigationIconPadding.roundToPx()
            val endWidth = actionsWidth + actionIconPadding.roundToPx()
            val titleBoxMax = ((width - navWidth - endWidth).coerceAtLeast(0) * 0.9f).roundToInt()
            val large = measurables[0].measure(Constraints(maxWidth = (width - 2 * padding).coerceAtLeast(0)))
            val small = measurables[1].measure(Constraints(maxWidth = (titleBoxMax - 2 * padding).coerceAtLeast(0)))
            val smallBoxWidth = small.width + 2 * padding
            var smallX = (width - smallBoxWidth) / 2
            if (smallX < navWidth) smallX = navWidth
            else if (smallX + smallBoxWidth > width - endWidth) smallX = width - endWidth - smallBoxWidth
            val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
            layout(width, constraints.maxHeight) {
                large.placeRelative(padding, collapsedHeight + (scrollBehavior?.state?.heightOffset ?: 0f).roundToInt())
                small.placeRelative(smallX + padding, (collapsedHeight - small.height) / 2)
            }
        }
    }
}

private class TitleMeasurement { var singleLine = true }
