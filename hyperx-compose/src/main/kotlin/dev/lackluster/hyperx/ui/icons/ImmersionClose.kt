package dev.lackluster.hyperx.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons

val MiuixIcons.ImmersionClose: ImageVector
    get() {
        if (_immersionClose != null) return _immersionClose!!
        _immersionClose = ImageVector.Builder("ImmersionClose", 26.0.dp, 26.0.dp, 26.0f, 26.0f).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5.314f, 20.686f)
                curveTo(5.4706f, 20.8426f, 5.5489f, 20.9208f, 5.6336f, 20.9621f)
                curveTo(5.789f, 21.0377f, 5.9705f, 21.0377f, 6.1258f, 20.9621f)
                curveTo(6.2106f, 20.9208f, 6.2888f, 20.8426f, 6.4454f, 20.686f)
                lineTo(13.0f, 14.1314f)
                lineTo(19.5453f, 20.6766f)
                curveTo(19.7018f, 20.8332f, 19.7801f, 20.9115f, 19.8649f, 20.9527f)
                curveTo(20.0202f, 21.0284f, 20.2017f, 21.0284f, 20.3571f, 20.9527f)
                curveTo(20.4418f, 20.9115f, 20.5201f, 20.8332f, 20.6767f, 20.6766f)
                curveTo(20.8332f, 20.5201f, 20.9115f, 20.4418f, 20.9528f, 20.3571f)
                curveTo(21.0284f, 20.2017f, 21.0284f, 20.0202f, 20.9528f, 19.8649f)
                curveTo(20.9115f, 19.7801f, 20.8332f, 19.7018f, 20.6767f, 19.5453f)
                lineTo(14.1314f, 13.0f)
                lineTo(20.686f, 6.4454f)
                curveTo(20.8426f, 6.2888f, 20.9208f, 6.2106f, 20.9621f, 6.1258f)
                curveTo(21.0377f, 5.9705f, 21.0377f, 5.789f, 20.9621f, 5.6336f)
                curveTo(20.9208f, 5.5489f, 20.8426f, 5.4706f, 20.686f, 5.314f)
                curveTo(20.5295f, 5.1575f, 20.4512f, 5.0792f, 20.3664f, 5.0379f)
                curveTo(20.2111f, 4.9623f, 20.0296f, 4.9623f, 19.8742f, 5.0379f)
                curveTo(19.7895f, 5.0792f, 19.7112f, 5.1575f, 19.5546f, 5.314f)
                lineTo(13.0f, 11.8686f)
                lineTo(6.4548f, 5.3234f)
                curveTo(6.2982f, 5.1668f, 6.2199f, 5.0886f, 6.1352f, 5.0473f)
                curveTo(5.9798f, 4.9717f, 5.7983f, 4.9717f, 5.643f, 5.0473f)
                curveTo(5.5582f, 5.0886f, 5.4799f, 5.1668f, 5.3234f, 5.3234f)
                curveTo(5.1668f, 5.4799f, 5.0886f, 5.5582f, 5.0473f, 5.643f)
                curveTo(4.9717f, 5.7983f, 4.9717f, 5.9798f, 5.0473f, 6.1352f)
                curveTo(5.0886f, 6.2199f, 5.1668f, 6.2982f, 5.3234f, 6.4548f)
                lineTo(11.8687f, 13.0f)
                lineTo(5.314f, 19.5546f)
                curveTo(5.1575f, 19.7112f, 5.0792f, 19.7895f, 5.0379f, 19.8742f)
                curveTo(4.9623f, 20.0296f, 4.9623f, 20.2111f, 5.0379f, 20.3664f)
                curveTo(5.0792f, 20.4512f, 5.1575f, 20.5295f, 5.314f, 20.686f)
                close()
            }
        }.build()
        return _immersionClose!!
    }
private var _immersionClose: ImageVector? = null