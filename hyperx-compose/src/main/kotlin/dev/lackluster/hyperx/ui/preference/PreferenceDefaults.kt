package dev.lackluster.hyperx.ui.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

object PreferenceDefaults {
    @Composable
    fun rightActionColors(
        color: Color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        disabledColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
    ): PreferenceActionColors {
        return PreferenceActionColors(
            color = color,
            disabledColor = disabledColor
        )
    }

    // 2. 未来你还可以把其他所有 Preference 通用的东西放在这里
    // 比如默认的左侧 Icon 尺寸配置、默认的列表 Item 高度等
    // val DefaultIconSize = 28.dp
}

@Immutable
class PreferenceActionColors(
    private val color: Color,
    private val disabledColor: Color
) {
    @Stable
    internal fun color(enabled: Boolean): Color =
        if (enabled) color else disabledColor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is PreferenceActionColors) return false
        if (color != other.color) return false
        if (disabledColor != other.disabledColor) return false
        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + disabledColor.hashCode()
        return result
    }
}