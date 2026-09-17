package dev.lackluster.hyperx.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.theme.UiStyle

@Immutable
data class HyperXLayoutConfig(
    val isSplitScreenEnabled: Boolean = true,
    val isBlurEnabled: Boolean = true,
    val uiStyle: UiStyle = UiStyle.Miuix,
    val lightBlurAlpha: Float = 0.8f,
    val darkBlurAlpha: Float = 0.7f,
) {
    /** MIUI blur is only applied in Miuix chrome; Material You uses tonal surfaces. */
    val isBlurActive: Boolean get() = isBlurEnabled && uiStyle.isMiuix
}

enum class PageLayoutMode {
    FULL_SCREEN,
    SPLIT_PRIMARY,
    SPLIT_SECONDARY
}

val LocalHyperXLayoutConfig = compositionLocalOf { HyperXLayoutConfig() }

/** Narrow local so blur/split toggles do not invalidate clip/overscroll modifiers. */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.Miuix }

val LocalPageMode = compositionLocalOf { PageLayoutMode.FULL_SCREEN }

val LocalLayoutPadding = compositionLocalOf { PaddingValues(0.dp) }
