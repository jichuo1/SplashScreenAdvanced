package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.lackluster.hyperx.R
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.ImageSource
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

enum class DropDownMode {
    Popup,
    Dialog
}

data class DropDownEntry<T>(
    val value: T,
    val title: String,
    val summary: String? = null,
    val icon: ImageSource? = null,
    val iconTint: Color? = null
)

@Composable
fun <T> DropDownPreference(
    title: String,
    value: T,
    entries: List<DropDownEntry<T>>,
    onValueChange: (T) -> Unit,
    icon: ImageIcon? = null,
    summary: String? = null,
    mode: DropDownMode = DropDownMode.Popup,
    showValue: Boolean = true,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
) {
    val updatedOnValueChange by rememberUpdatedState(onValueChange)

    val wrappedEntries = remember(entries) {
        entries.map { entry ->
            SpinnerEntry(
                icon = entry.icon?.let { imageSource ->
                    @Composable { spinnerModifier ->
                        RenderImageSource(
                            source = imageSource,
                            modifier = spinnerModifier,
                            tint = entry.iconTint
                        )
                    }
                },
                title = entry.title,
                summary = entry.summary,
            )
        }
    }

    val selectedIndex = entries.indexOfFirst { it.value == value }.coerceAtLeast(0)

    val startAction: @Composable (() -> Unit)? = icon?.let { { PreferenceIconSlot(icon = it) } }

    val handleIndexChange: (Int) -> Unit = { newIndex ->
        entries.getOrNull(newIndex)?.let { selectedEntry ->
            updatedOnValueChange(selectedEntry.value)
        }
    }

    when (mode) {
        DropDownMode.Dialog -> {
            OverlaySpinnerPreference(
                items = wrappedEntries,
                selectedIndex = selectedIndex,
                title = title,
                dialogButtonString = stringResource(R.string.button_cancel),
                titleColor = titleColor,
                summary = summary,
                summaryColor = summaryColor,
                startAction = startAction,
                enabled = enabled,
                showValue = showValue,
                onSelectedIndexChange = handleIndexChange,
            )
        }
        else -> {
            OverlaySpinnerPreference(
                items = wrappedEntries,
                selectedIndex = selectedIndex,
                title = title,
                titleColor = titleColor,
                summary = summary,
                summaryColor = summaryColor,
                startAction = startAction,
                enabled = enabled,
                showValue = showValue,
                onSelectedIndexChange = handleIndexChange,
            )
        }
    }
}

@Composable
fun <T: Any> DropDownPreference(
    key: PreferenceKey<T>,
    title: String,
    entries: List<DropDownEntry<T>>,
    icon: ImageIcon? = null,
    summary: String? = null,
    mode: DropDownMode = DropDownMode.Popup,
    showValue: Boolean = true,
    enabled: Boolean = true,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    onValueChange: (T) -> Unit = {},
) {
    val savedValue = rememberPreferenceState(key)

    DropDownPreference(
        title = title,
        value = savedValue.value,
        entries = entries,
        onValueChange = { newValue ->
            savedValue.value = newValue
            onValueChange(newValue)
        },
        icon = icon,
        summary = summary,
        mode = mode,
        showValue = showValue,
        enabled = enabled,
        titleColor = titleColor,
        summaryColor = summaryColor
    )
}

@Composable
fun RenderImageSource(
    source: ImageSource,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val colorFilter = tint?.let { ColorFilter.tint(it) }

    when (source) {
        is ImageSource.Vector -> {
            Image(
                imageVector = source.vector,
                contentDescription = null,
                modifier = modifier,
                colorFilter = colorFilter
            )
        }
        is ImageSource.Res -> {
            Image(
                painter = painterResource(id = source.id),
                contentDescription = null,
                modifier = modifier,
                colorFilter = colorFilter
            )
        }
        is ImageSource.Bitmap -> {
            Image(
                bitmap = source.bitmap,
                contentDescription = null,
                modifier = modifier,
                colorFilter = colorFilter
            )
        }
    }
}