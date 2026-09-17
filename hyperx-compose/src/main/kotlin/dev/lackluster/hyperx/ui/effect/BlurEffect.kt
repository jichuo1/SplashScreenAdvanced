package dev.lackluster.hyperx.ui.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.lackluster.hyperx.ui.layout.LocalHyperXLayoutConfig
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(
    containerColor: Color = MiuixTheme.colorScheme.surface
): LayerBackdrop? {
    val config = LocalHyperXLayoutConfig.current
    if (!config.isBlurActive || !isRenderEffectSupported() || !isRuntimeShaderSupported()) {
        return null
    }
    return rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }
}