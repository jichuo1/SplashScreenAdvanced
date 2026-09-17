package dev.lackluster.hyperx.ui.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import dev.lackluster.hyperx.ui.theme.hyperXBorder
import dev.lackluster.hyperx.ui.theme.hyperXClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    colors: TabRowColors = TabRowDefaults.tabRowColors(),
    cornerRadius: Dp = TabRowDefaults.TabRowCornerRadius,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tabs.forEachIndexed { index, tabText ->
            val isSelected = selectedTabIndex == index

            val bgColor by animateColorAsState(
                targetValue = colors.backgroundColor(isSelected),
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "TabBgColor"
            )
            val borderColor by animateColorAsState(
                targetValue = colors.borderColor(isSelected),
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "TabBorderColor"
            )
            val contentColor by animateColorAsState(
                targetValue = colors.contentColor(isSelected),
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "TabContentColor"
            )

            Surface(
                onClick = { onTabSelected(index) },
                color = bgColor,
                modifier = Modifier
                    .weight(1.0f)
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .hyperXBorder(1.5.dp, borderColor, cornerRadius)
                    .hyperXClip(cornerRadius)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabText,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.subtitle
                    )
                }
            }
        }
    }
}

object TabRowDefaults {
    val TabRowCornerRadius = 12.dp

    @Composable
    fun tabRowColors(
        borderColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        backgroundColor: Color = if (isSystemInDarkTheme()) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.surfaceContainer,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.6f),
        selectedBorderColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
        selectedBackgroundColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
        selectedContentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer
    ): TabRowColors = TabRowColors(
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        selectedBorderColor = selectedBorderColor,
        selectedBackgroundColor = selectedBackgroundColor,
        selectedContentColor = selectedContentColor
    )
}

@Immutable
class TabRowColors(
    private val borderColor: Color,
    private val backgroundColor: Color,
    private val contentColor: Color,
    private val selectedBorderColor: Color,
    private val selectedBackgroundColor: Color,
    private val selectedContentColor: Color
) {
    @Stable
    internal fun borderColor(selected: Boolean): Color =
        if (selected) selectedBorderColor else borderColor

    @Stable
    internal fun backgroundColor(selected: Boolean): Color =
        if (selected) selectedBackgroundColor else backgroundColor

    @Stable
    internal fun contentColor(selected: Boolean): Color =
        if (selected) selectedContentColor else contentColor
}