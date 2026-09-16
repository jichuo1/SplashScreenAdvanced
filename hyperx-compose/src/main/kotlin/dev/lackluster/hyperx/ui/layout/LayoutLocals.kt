package dev.lackluster.hyperx.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

data class HyperXLayoutConfig(
    val isSplitScreenEnabled: Boolean = true,
    val isBlurEnabled: Boolean = true,
    val lightBlurAlpha: Float = 0.8f,
    val darkBlurAlpha: Float = 0.7f,
)

enum class PageLayoutMode {
    FULL_SCREEN,
    SPLIT_PRIMARY,
    SPLIT_SECONDARY
}

val LocalHyperXLayoutConfig = compositionLocalOf { HyperXLayoutConfig() }

val LocalPageMode = compositionLocalOf { PageLayoutMode.FULL_SCREEN }

val LocalLayoutPadding = compositionLocalOf { PaddingValues(0.dp) }