package dev.lackluster.hyperx.ui.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.lackluster.hyperx.ui.layout.LocalUiStyle
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun Modifier.hyperXClip(cornerRadius: Dp): Modifier {
    return if (LocalUiStyle.current.isMaterialYou) {
        clip(remember(cornerRadius) { RoundedCornerShape(cornerRadius) })
    } else {
        squircleClip(cornerRadius)
    }
}

@Composable
fun Modifier.hyperXSurface(color: Color, cornerRadius: Dp): Modifier {
    return if (LocalUiStyle.current.isMaterialYou) {
        clip(remember(cornerRadius) { RoundedCornerShape(cornerRadius) }).background(color)
    } else {
        squircleSurface(color = color, cornerRadius = cornerRadius)
    }
}

@Composable
fun Modifier.hyperXBorder(width: Dp, color: Color, cornerRadius: Dp): Modifier {
    return if (LocalUiStyle.current.isMaterialYou) {
        border(width, color, remember(cornerRadius) { RoundedCornerShape(cornerRadius) })
    } else {
        squircleBorder({ width }, { color }, cornerRadius)
    }
}

@Composable
fun Modifier.hyperXOverScrollVertical(): Modifier {
    return if (LocalUiStyle.current.isMaterialYou) this else overScrollVertical()
}

@Composable
fun Modifier.hyperXScrollEndHaptic(): Modifier {
    return if (LocalUiStyle.current.isMaterialYou) this else scrollEndHaptic()
}

@Composable
fun rememberHyperXListOverscrollEffect(): OverscrollEffect? {
    return if (LocalUiStyle.current.isMaterialYou) {
        rememberOverscrollEffect()
    } else {
        null
    }
}

@Composable
fun currentCardPressFeedback(): PressFeedbackType =
    if (LocalUiStyle.current.isMaterialYou) {
        PressFeedbackType.None
    } else {
        PressFeedbackType.Sink
    }

@Composable
fun currentCardShowIndication(): Boolean =
    LocalUiStyle.current.isMaterialYou
