package dev.lackluster.hyperx.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.layout.LocalUiStyle
import top.yukonga.miuix.kmp.squircle.squircleClip

enum class IconSize(val dp: Dp) {
    Small(dp = 28.dp),
    Medium(dp = 38.dp),
    Large(dp = 44.dp),
    App(dp = 40.dp),
    SeekBar(dp = 26.dp),
    Unspecified(dp = Dp.Unspecified)
}

sealed interface ImageSource {
    @JvmInline value class Res(@DrawableRes val id: Int) : ImageSource
    @JvmInline value class Vector(val vector: ImageVector) : ImageSource
    @JvmInline value class Bitmap(val bitmap: ImageBitmap) : ImageSource
}

@Immutable
data class ImageIcon(
    val source: ImageSource,
    val size: IconSize = IconSize.Small,
    val customSizeDp: Dp = Dp.Unspecified,
    val cornerRadius: Dp = Dp.Unspecified
) {
    constructor(@DrawableRes resId: Int, size: IconSize = IconSize.Small, cornerRadius: Dp = Dp.Unspecified) :
            this(ImageSource.Res(resId), size, Dp.Unspecified, cornerRadius)

    constructor(vector: ImageVector, size: IconSize = IconSize.Small, cornerRadius: Dp = Dp.Unspecified) :
            this(ImageSource.Vector(vector), size, Dp.Unspecified, cornerRadius)

    constructor(bitmap: ImageBitmap, size: IconSize = IconSize.Small, cornerRadius: Dp = Dp.Unspecified) :
            this(ImageSource.Bitmap(bitmap), size, Dp.Unspecified, cornerRadius)

    val actualSizeDp: Dp get() = if (size != IconSize.Unspecified) size.dp else customSizeDp
}

@Composable
fun AdaptiveIcon(
    icon: ImageIcon,
    modifier: Modifier = Modifier
) {
    val sizeDp = icon.actualSizeDp
    val cornerRadius = icon.cornerRadius
    val materialYou = LocalUiStyle.current.isMaterialYou
    val roundedClip = remember(cornerRadius) {
        RoundedCornerShape(if (cornerRadius == Dp.Unspecified) 0.dp else cornerRadius)
    }
    val cornerClip = if (cornerRadius == Dp.Unspecified) {
        Modifier
    } else if (cornerRadius >= sizeDp / 2) {
        Modifier.clip(CircleShape)
    } else if (materialYou) {
        Modifier.clip(roundedClip)
    } else {
        Modifier.squircleClip(cornerRadius)
    }
    val finalModifier = modifier
        .size(sizeDp)
        .then(cornerClip)

    when (val src = icon.source) {
        is ImageSource.Res -> Image(painterResource(src.id), null, finalModifier)
        is ImageSource.Vector -> Image(src.vector, null, finalModifier)
        is ImageSource.Bitmap -> Image(src.bitmap, null, finalModifier)
    }
}

@Composable
fun PreferenceIconSlot(
    icon: ImageIcon,
    modifier: Modifier = Modifier
) {
    AdaptiveIcon(
        icon = icon,
        modifier = modifier.padding(end = 8.dp)
    )
}