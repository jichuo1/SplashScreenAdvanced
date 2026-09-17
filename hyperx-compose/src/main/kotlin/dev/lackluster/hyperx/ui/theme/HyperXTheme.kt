package dev.lackluster.hyperx.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.defaultTextStyles

@Composable
fun HyperXTheme(
    uiStyle: UiStyle = UiStyle.Miuix,
    content: @Composable () -> Unit
) {
    val controller = remember(uiStyle) {
        ThemeController(
            colorSchemeMode = if (uiStyle.isMaterialYou) {
                ColorSchemeMode.MonetSystem
            } else {
                ColorSchemeMode.System
            },
            colorSpec = ThemeColorSpec.Spec2021,
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }
    val textStyles = remember(uiStyle) {
        if (uiStyle.isMaterialYou) materialYouTextStyles() else defaultTextStyles()
    }
    MiuixTheme(
        controller = controller,
        textStyles = textStyles,
    ) {
        val colors = MiuixTheme.colorScheme
        ApplySystemBars(surfaceArgb = colors.surface.toArgb(), lightIcons = colors.surface.luminance() >= 0.5f)
        if (uiStyle.isMaterialYou) {
            val indication = remember(colors.onSurface) { materialYouRipple(colors.onSurface) }
            CompositionLocalProvider(
                LocalIndication provides indication,
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun ApplySystemBars(surfaceArgb: Int, lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
        window.setBackgroundDrawable(ColorDrawable(surfaceArgb))
    }
}

private fun materialYouTextStyles() = defaultTextStyles(
    title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal),
    title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    title3 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    title4 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    subtitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    headline1 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    body1 = TextStyle(fontSize = 16.sp, letterSpacing = 0.15.sp),
    body2 = TextStyle(fontSize = 14.sp, letterSpacing = 0.25.sp),
)
