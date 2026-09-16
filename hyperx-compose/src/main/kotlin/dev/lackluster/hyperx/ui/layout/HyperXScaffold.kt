package dev.lackluster.hyperx.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.effect.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HyperXScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable ((contentPadding: PaddingValues) -> Unit)? = null,
    bottomBar: @Composable ((contentPadding: PaddingValues) -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical),
    blurTopBar: Boolean = false,
    blurBottomBar: Boolean = false,
    blurTintAlpha: Float = 0.8f,
    layoutPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (PaddingValues) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val maxTopBlurRadius = 25f
    val maxBottomBlurRadius = 25f

    val backdrop = rememberBlurBackdrop(containerColor)

    Scaffold(
        modifier = modifier,
        topBar = @Composable {
            topBar?.let {
                if (blurTopBar && backdrop != null) {
                    DynamicBlurBox(
                        backdrop = backdrop,
                        containerColor = containerColor,
                        blurRadius = maxTopBlurRadius,
                        blurTintAlpha = blurTintAlpha,
                    ) {
                        it(layoutPadding)
                    }
                } else {
                    it(layoutPadding)
                }
            }
        },
        bottomBar = @Composable {
            bottomBar?.let {
                if (blurBottomBar && backdrop != null) {
                    DynamicBlurBox(
                        backdrop = backdrop,
                        containerColor = containerColor,
                        blurRadius = maxBottomBlurRadius,
                        blurTintAlpha = blurTintAlpha,
                    ) {
                        it(layoutPadding)
                    }
                } else {
                    it(layoutPadding)
                }
            }
        },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        Box(
            if ((blurTopBar || blurBottomBar) && backdrop != null) {
                Modifier.layerBackdrop(backdrop)
            } else {
                Modifier
            }
        ) {
            content(
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection) +
                            layoutPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding() +
                            layoutPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection) +
                            layoutPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding() +
                            layoutPadding.calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun DynamicBlurBox(
    backdrop: LayerBackdrop?,
    containerColor: Color,
    blurRadius: Float,
    blurTintAlpha: Float,
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        content()
        return
    }

    val blurColors = remember(containerColor, blurTintAlpha) {
        BlurColors(
            blendColors = listOf(BlendColorEntry(containerColor.copy(alpha = blurTintAlpha)))
        )
    }

    Box(
        modifier = Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = blurRadius,
            colors = blurColors,
        )
    ) {
        content()
    }
}