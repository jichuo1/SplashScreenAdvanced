package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.animation.LocalPageMotion
import dev.lackluster.hyperx.ui.animation.PageMotionAnchor
import dev.lackluster.hyperx.ui.component.CardDefaults
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val density = LocalDensity.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hidingSource = motion?.isHidingSourceTitle(title) == true
    val resolvedTitleColor = if (hidingSource) {
        BasicComponentDefaults.titleColor(Color.Transparent)
    } else {
        titleColor
    }
    val cornerRadiusPx = with(density) { CardDefaults.CornerRadius.toPx() }
    val fontSizePx = with(density) { MiuixTheme.textStyles.body1.fontSize.toPx() }
    val sourceColor = MiuixTheme.colorScheme.onSurface

    Box(modifier = Modifier.onGloballyPositioned { coordinates = it }) {
        ArrowPreference(
            title = title,
            titleColor = resolvedTitleColor,
            summary = summary,
            summaryColor = summaryColor,
            startAction = icon?.let { imageIcon ->
                { PreferenceIconSlot(icon = imageIcon) }
            },
            endActions = {
                value?.let {
                    Text(
                        modifier = Modifier.widthIn(max = 130.dp),
                        text = it,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = PreferenceDefaults.rightActionColors().color(enabled),
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                }
            },
            bottomAction = bottomAction,
            holdDownState = holdDownState || hidingSource,
            onClick = {
                if (!enabled) return@ArrowPreference
                coordinates?.let { layout ->
                    val row = layout.boundsInWindow()
                    val iconInset = with(density) { if (icon != null) 52.dp.toPx() else 20.dp.toPx() }
                    val topInset = with(density) { 14.dp.toPx() }
                    val titleWidth = (fontSizePx * title.length * 0.62f)
                        .coerceAtMost((row.width - iconInset - with(density) { 36.dp.toPx() }).coerceAtLeast(1f))
                    motion?.captureSource(
                        PageMotionAnchor(
                            text = title,
                            bounds = Rect(
                                left = row.left + iconInset,
                                top = row.top + topInset,
                                right = row.left + iconInset + titleWidth,
                                bottom = row.top + topInset + fontSizePx * 1.35f
                            ),
                            rowBounds = row,
                            fontSizePx = fontSizePx,
                            color = sourceColor,
                            fontWeight = FontWeight.Medium,
                        ),
                        cornerRadiusPx = cornerRadiusPx,
                    )
                }
                onClick?.invoke()
            },
            enabled = enabled,
        )
    }
}
