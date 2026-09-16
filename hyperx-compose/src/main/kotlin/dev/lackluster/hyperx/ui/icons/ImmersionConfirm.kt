package dev.lackluster.hyperx.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons

val MiuixIcons.ImmersionConfirm: ImageVector
    get() {
        if (_immersionConfirm != null) return _immersionConfirm!!
        _immersionConfirm = ImageVector.Builder("ImmersionConfirm", 26.0.dp, 26.0.dp, 26.0f, 26.0f).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(23.571f, 4.8182f)
                curveTo(23.5176f, 4.7394f, 23.428f, 4.6728f, 23.2486f, 4.5396f)
                curveTo(23.0692f, 4.4064f, 22.9795f, 4.3398f, 22.8887f, 4.3115f)
                curveTo(22.726f, 4.2607f, 22.549f, 4.2869f, 22.4079f, 4.3825f)
                curveTo(22.3292f, 4.4358f, 22.2626f, 4.5255f, 22.1294f, 4.7049f)
                lineTo(10.6253f, 20.1956f)
                lineTo(3.8893f, 13.13f)
                curveTo(3.7352f, 12.9682f, 3.6581f, 12.8874f, 3.5734f, 12.8441f)
                curveTo(3.4216f, 12.7665f, 3.2428f, 12.7622f, 3.0875f, 12.8325f)
                curveTo(3.0009f, 12.8717f, 2.92f, 12.9488f, 2.7583f, 13.1029f)
                curveTo(2.5966f, 13.2571f, 2.5157f, 13.3342f, 2.4724f, 13.4189f)
                curveTo(2.3948f, 13.5707f, 2.3905f, 13.7495f, 2.4608f, 13.9047f)
                curveTo(2.5f, 13.9914f, 2.5771f, 14.0723f, 2.7313f, 14.234f)
                lineTo(10.0438f, 21.9044f)
                curveTo(10.2962f, 22.1691f, 10.4223f, 22.3014f, 10.5659f, 22.3437f)
                curveTo(10.6919f, 22.3809f, 10.8269f, 22.3726f, 10.9474f, 22.3203f)
                curveTo(11.0847f, 22.2607f, 11.1937f, 22.1139f, 11.4117f, 21.8203f)
                lineTo(23.4139f, 5.6588f)
                curveTo(23.5471f, 5.4794f, 23.6137f, 5.3898f, 23.642f, 5.2989f)
                curveTo(23.6927f, 5.1362f, 23.6666f, 4.9593f, 23.571f, 4.8182f)
                close()
            }
        }.build()
        return _immersionConfirm!!
    }
private var _immersionConfirm: ImageVector? = null