package dev.lackluster.hyperx.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun Colors.contentColorFor(backgroundColor: Color): Color {
    return when (backgroundColor) {
        primary -> onPrimary
        primaryVariant -> onPrimary
        secondary -> onSecondary
        secondaryVariant -> onSecondary
        background -> onBackground
        surface -> onSurface
        else -> Color.Unspecified
    }
}

@Composable
@ReadOnlyComposable
fun contentColorFor(backgroundColor: Color) =
    MiuixTheme.colorScheme.contentColorFor(backgroundColor).takeOrElse { LocalContentColor.current }

internal const val DisabledAlpha = 0.38f