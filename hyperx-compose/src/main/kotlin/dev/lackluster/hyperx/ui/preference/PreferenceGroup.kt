package dev.lackluster.hyperx.ui.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.component.Card
import dev.lackluster.hyperx.ui.component.CardColors
import dev.lackluster.hyperx.ui.component.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    position: ItemPosition = ItemPosition.Middle,
    titleColor: Color = Color.Unspecified,
    cardColors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    val actualTitleColor = if (titleColor == Color.Unspecified) MiuixTheme.colorScheme.onBackgroundVariant else titleColor

    Column(modifier = modifier) {
        title?.let {
            SmallTitle(
                text = it,
                modifier = Modifier.padding(top = 6.dp),
                textColor = actualTitleColor
            )
        }

        val cardTopPadding =
            if (title != null) 0.dp
            else if (position == ItemPosition.First || position == ItemPosition.Single) 12.dp
            else 6.dp
        val cardBottomPadding =
            if (position == ItemPosition.Last || position == ItemPosition.Single) 12.dp else 6.dp

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = cardTopPadding, bottom = cardBottomPadding),
            colors = cardColors,
            content = content
        )
    }
}

enum class ItemPosition {
    Single,
    First,
    Middle,
    Last
}