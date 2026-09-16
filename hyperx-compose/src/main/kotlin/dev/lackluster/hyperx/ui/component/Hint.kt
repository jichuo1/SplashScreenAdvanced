package dev.lackluster.hyperx.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.R
import dev.lackluster.hyperx.ui.icons.HintClose
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Hint(
    text: String,
    modifier: Modifier = Modifier,
    foregroundColor: Color = colorResource(R.color.hyperx_hint_fg),
    backgroundColor: Color = colorResource(R.color.hyperx_hint_bg),
    contentPadding: PaddingValues = CardDefaults.contentPadding,
    closeable: Boolean = false,
    onClose: () -> Unit = {}
) {
    val layoutDirection = LocalLayoutDirection.current

    val compensatedPadding = remember(contentPadding, closeable, layoutDirection) {
        if (closeable) {
            val endPadding = contentPadding.calculateEndPadding(layoutDirection)
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = (endPadding - 10.5.dp).coerceAtLeast(0.dp),
                bottom = contentPadding.calculateBottomPadding()
            )
        } else {
            contentPadding
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = foregroundColor
        ),
        contentPadding = compensatedPadding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1.0f),
                text = text,
                fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                fontWeight = FontWeight.Medium,
                color = foregroundColor,
                textAlign = TextAlign.Start
            )

            if (closeable) {
                IconButton(
                    modifier = Modifier.padding(start = 5.5.dp).size(32.dp),
                    onClick = onClose
                ) {
                    Icon(
                        modifier = Modifier.size(11.dp),
                        imageVector = MiuixIcons.HintClose,
                        contentDescription = "Close Hint",
                        tint = foregroundColor
                    )
                }
            }
        }
    }
}