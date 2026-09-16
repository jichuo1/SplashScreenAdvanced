package dev.lackluster.hyperx.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.theme.DisabledAlpha
import dev.lackluster.hyperx.ui.theme.contentColorFor
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    colors: CardColors = CardDefaults.cardColors(),
    contentPadding: PaddingValues = CardDefaults.contentPaddingZero,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides colors.contentColor(enabled = true)) {
        Column(
            modifier = modifier
                .squircleSurface(
                    color = colors.containerColor(enabled = true),
                    cornerRadius = cornerRadius,
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    colors: CardColors = CardDefaults.cardColors(),
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = CardDefaults.contentPaddingZero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.squircleClip(cornerRadius),
        enabled = enabled,
        color = colors.containerColor(enabled),
        contentColor = colors.contentColor(enabled),
        interactionSource = actualInteractionSource,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

object CardDefaults {
    val CornerRadius = 16.dp

    val contentPaddingZero: PaddingValues = PaddingValues.Zero
    val contentPadding: PaddingValues = PaddingValues(16.dp)

    @Composable
    fun cardColors(): CardColors {
        val containerColor = MiuixTheme.colorScheme.background
        val contentColor = contentColorFor(containerColor)

        return CardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor
                .copy(alpha = DisabledAlpha)
                .compositeOver(containerColor),
            disabledContentColor = contentColor.copy(alpha = DisabledAlpha),
        )
    }

    @Composable
    fun cardColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = contentColorFor(containerColor),
        disabledContainerColor: Color = Color.Unspecified,
        disabledContentColor: Color = contentColor.copy(alpha = DisabledAlpha),
    ): CardColors =
        cardColors().copy(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        )
}

@Immutable
class CardColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
) {
    fun copy(
        containerColor: Color = this.containerColor,
        contentColor: Color = this.contentColor,
        disabledContainerColor: Color = this.disabledContainerColor,
        disabledContentColor: Color = this.disabledContentColor,
    ) = CardColors(
        containerColor.takeOrElse { this.containerColor },
        contentColor.takeOrElse { this.contentColor },
        disabledContainerColor.takeOrElse { this.disabledContainerColor },
        disabledContentColor.takeOrElse { this.disabledContentColor },
    )

    @Stable
    internal fun containerColor(enabled: Boolean): Color =
        if (enabled) containerColor else disabledContainerColor

    @Stable
    internal fun contentColor(enabled: Boolean) =
        if (enabled) contentColor else disabledContentColor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is CardColors) return false

        if (containerColor != other.containerColor) return false
        if (contentColor != other.contentColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (disabledContentColor != other.disabledContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + disabledContentColor.hashCode()
        return result
    }
}