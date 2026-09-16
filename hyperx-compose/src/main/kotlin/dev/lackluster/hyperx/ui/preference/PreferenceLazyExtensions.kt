package dev.lackluster.hyperx.ui.preference

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import dev.lackluster.hyperx.ui.component.CardColors
import dev.lackluster.hyperx.ui.component.CardDefaults

private val DefaultItemPlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold
)
private val DefaultItemFadeSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow
)

fun LazyListScope.itemPreferenceGroup(
    key: Any? = null,
    visible: Boolean = true,
    title: String? = null,
    position: ItemPosition = ItemPosition.Middle,
    titleColor: Color = Color.Unspecified,
    cardColors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    item(key = key, contentType = "PreferenceGroup") {
        PreferenceGroup(
            modifier = Modifier.animateItem(
                fadeInSpec = DefaultItemFadeSpec,
                placementSpec = DefaultItemPlacementSpec,
                fadeOutSpec = DefaultItemFadeSpec
            ),
            title = title,
            position = position,
            titleColor = titleColor,
            cardColors = cardColors ?: CardDefaults.cardColors(),
            content = content
        )
    }
}

fun LazyListScope.itemPreferenceGroup(
    @StringRes titleRes: Int,
    key: Any? = titleRes,
    visible: Boolean = true,
    position: ItemPosition = ItemPosition.Middle,
    titleColor: Color = Color.Unspecified,
    cardColors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    item(key = key, contentType = "PreferenceGroup") {
        PreferenceGroup(
            modifier = Modifier.animateItem(
                fadeInSpec = DefaultItemFadeSpec,
                placementSpec = DefaultItemPlacementSpec,
                fadeOutSpec = DefaultItemFadeSpec
            ),
            title = stringResource(titleRes),
            position = position,
            titleColor = titleColor,
            cardColors = cardColors ?: CardDefaults.cardColors(),
            content = content
        )
    }
}

fun LazyListScope.itemAnimated(
    key: Any,
    visible: Boolean = true,
    contentType: Any? = null,
    content: @Composable () -> Unit
) {
    if (visible) {
        item(key = key, contentType = contentType) {
            Box(
                modifier = Modifier.animateItem(
                    fadeInSpec = DefaultItemFadeSpec,
                    placementSpec = DefaultItemPlacementSpec,
                    fadeOutSpec = DefaultItemFadeSpec
                )
            ) {
                content()
            }
        }
    }
}

fun LazyListScope.itemAnimatedColumn(
    key: Any,
    visible: Boolean = true,
    contentType: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    item(key = key, contentType = contentType) {
        Column(
            modifier = Modifier.animateItem(
                fadeInSpec = DefaultItemFadeSpec,
                placementSpec = DefaultItemPlacementSpec,
                fadeOutSpec = DefaultItemFadeSpec
            ),
            content = content
        )
    }
}