package dev.lackluster.hyperx.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons

val MiuixIcons.HintClose: ImageVector
    get() {
        if (_hintClose != null) return _hintClose!!
        _hintClose = ImageVector.Builder("HintClose", 11.0.dp, 11.0.dp, 11.0f, 11.0f).apply {
            path(
                fill = SolidColor(Color(0xFF3482FF)),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(0.8492f, 10.2621f)
                curveTo(0.4262f, 9.8391f, 0.4262f, 9.1531f, 0.8492f, 8.7301f)
                lineTo(3.7873f, 5.792f)
                lineTo(0.7616f, 2.7664f)
                curveTo(0.3386f, 2.3433f, 0.3386f, 1.6574f, 0.7616f, 1.2343f)
                curveTo(1.1847f, 0.8113f, 1.8706f, 0.8113f, 2.2937f, 1.2343f)
                lineTo(5.3193f, 4.26f)
                lineTo(8.5096f, 1.0697f)
                curveTo(8.9326f, 0.6467f, 9.6186f, 0.6467f, 10.0416f, 1.0697f)
                curveTo(10.4647f, 1.4928f, 10.4647f, 2.1787f, 10.0416f, 2.6018f)
                lineTo(6.8514f, 5.792f)
                lineTo(9.954f, 8.8947f)
                curveTo(10.3771f, 9.3177f, 10.3771f, 10.0037f, 9.954f, 10.4267f)
                curveTo(9.531f, 10.8498f, 8.845f, 10.8498f, 8.422f, 10.4267f)
                lineTo(5.3193f, 7.3241f)
                lineTo(2.3813f, 10.2621f)
                curveTo(1.9582f, 10.6852f, 1.2723f, 10.6852f, 0.8492f, 10.2621f)
                close()
            }
        }.build()
        return _hintClose!!
    }

private var _hintClose: ImageVector? = null
