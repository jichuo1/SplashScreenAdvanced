package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.animation.LocalPageMotion
import dev.lackluster.hyperx.ui.animation.LocalPageMotionKey
import dev.lackluster.hyperx.ui.animation.PageMotionAnchor
import dev.lackluster.hyperx.ui.animation.unclippedBoundsInWindow
import dev.lackluster.hyperx.ui.component.CardDefaults
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

@Composable
fun TextPreference(
    title: String,
    icon: ImageIcon? = null,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    bottomAction: (@Composable () -> Unit)? = null,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onClick: (() -> Unit)? = null,
) {
    val motion = LocalPageMotion.current
    val owner = LocalPageMotionKey.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val anchorId = rememberSaveable { UUID.randomUUID().toString() }
    val coordinates = remember { PreferenceCoordinates() }
    val fontSize = MiuixTheme.textStyles.headline1.fontSize
    val fontSizePx = with(density) { fontSize.toPx() }
    val color = if (enabled) titleColor.color else titleColor.disabledColor
    val cornerRadiusPx = with(density) { CardDefaults.CornerRadius.toPx() }
    fun anchor(): PageMotionAnchor? {
        val row = coordinates.row?.takeIf { it.isAttached } ?: return null
        val text = coordinates.title?.takeIf { it.isAttached } ?: return null
        return PageMotionAnchor(title, text.unclippedBoundsInWindow(), row.unclippedBoundsInWindow(),
            fontSizePx, color, FontWeight.Medium, owner ?: return null, anchorId, coordinates.singleLine)
    }

    Box(Modifier.onGloballyPositioned {
        coordinates.row = it
        anchor()?.let { measured -> motion?.reportSource(measured) }
    }) {
        // BasicComponent's content slot preserves its padding, click feedback and row measurement,
        // while giving us the real Text bounds instead of estimating width from the character count.
        BasicComponent(
            startAction = icon?.let { imageIcon -> { PreferenceIconSlot(imageIcon) } },
            endActions = {
                Row(Modifier.padding(end = 8.dp).align(Alignment.CenterVertically).weight(1f, fill = false)) {
                    value?.let {
                        Text(
                            text = it,
                            modifier = Modifier.widthIn(max = 130.dp),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = PreferenceDefaults.rightActionColors().color(enabled),
                            textAlign = TextAlign.End,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                    }
                }
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    modifier = Modifier.size(10.dp, 16.dp).align(Alignment.CenterVertically)
                        .graphicsLayer { scaleX = if (direction == LayoutDirection.Rtl) -1f else 1f },
                )
            },
            bottomAction = bottomAction,
            holdDownState = holdDownState,
            enabled = enabled,
            onClick = onClick?.let { click ->
                {
                    if (motion == null) click()
                    else if (motion.canPush) motion.withSource(anchor(), cornerRadiusPx, click)
                }
            },
        ) {
            Text(
                text = title,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = color,
                onTextLayout = { coordinates.singleLine = it.lineCount == 1 },
                modifier = Modifier
                    .onGloballyPositioned {
                        coordinates.title = it
                        anchor()?.let { measured -> motion?.reportSource(measured) }
                    }
                    .graphicsLayer { alpha = if (motion?.isHidingSourceTitle(owner, anchorId) == true) 0f else 1f },
            )
            summary?.let {
                Text(text = it, fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = if (enabled) summaryColor.color else summaryColor.disabledColor)
            }
        }
    }
}

private class PreferenceCoordinates {
    var row: LayoutCoordinates? = null
    var title: LayoutCoordinates? = null
    var singleLine = true
}
