package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    ArrowPreference(
        title = title,
        titleColor = titleColor,
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
        holdDownState = holdDownState,
        onClick = {
            if (enabled) {
                onClick?.invoke()
            }
        },
        enabled = enabled,
    )
}