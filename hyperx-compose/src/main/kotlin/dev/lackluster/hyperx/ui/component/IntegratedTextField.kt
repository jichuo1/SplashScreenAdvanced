package dev.lackluster.hyperx.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.preference.PreferenceDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IntegratedTextField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    interactionSource: MutableInteractionSource? = null,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val textColor = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    val textFieldValue = remember {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    if (text != textFieldValue.value.text) {
        textFieldValue.value = textFieldValue.value.copy(
            text = text,
            selection = TextRange(text.length)
        )
    }

    BasicTextField(
        value = textFieldValue.value,
        onValueChange = { newValue ->
            textFieldValue.value = newValue
            onTextChange(newValue.text)
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = MiuixTheme.textStyles.main.copy(
            textAlign = TextAlign.End,
            color = textColor
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        interactionSource = actualInteractionSource,
        decorationBox = @Composable { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(insideMargin),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (text.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        color = PreferenceDefaults.rightActionColors().color(enabled),
                        textAlign = TextAlign.End,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}