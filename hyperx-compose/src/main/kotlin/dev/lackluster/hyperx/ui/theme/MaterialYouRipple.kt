package dev.lackluster.hyperx.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Dp

internal fun materialYouRipple(color: Color): IndicationNodeFactory = MaterialYouRipple(color)

private class MaterialYouRipple(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return createRippleModifierNode(
            interactionSource = interactionSource,
            bounded = true,
            radius = Dp.Unspecified,
            color = ColorProducer { color },
            rippleAlpha = { MaterialYouRippleAlpha },
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MaterialYouRipple) return false
        return color == other.color
    }

    override fun hashCode(): Int = color.hashCode()
}

private val MaterialYouRippleAlpha = RippleAlpha(
    pressedAlpha = 0.12f,
    focusedAlpha = 0.12f,
    draggedAlpha = 0.16f,
    hoveredAlpha = 0.08f,
)
