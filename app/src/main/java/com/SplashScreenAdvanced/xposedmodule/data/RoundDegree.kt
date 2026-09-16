package com.SplashScreenAdvanced.xposedmodule.data

import com.SplashScreenAdvanced.xposedmodule.data.RoundDegree.Circle
import com.SplashScreenAdvanced.xposedmodule.data.RoundDegree.MIUIWidget
import com.SplashScreenAdvanced.xposedmodule.data.RoundDegree.NotDrawRoundCorner
import com.SplashScreenAdvanced.xposedmodule.data.RoundDegree.RoundCorner


/**
 * 圆角程度
 *
 * [NotDrawRoundCorner] 不绘制圆角
 * [RoundCorner] 绘制圆角图标
 * [MIUIWidget] 绘制小部件圆角
 * [Circle] 绘制圆形图标
 */
enum class RoundDegree {
    NotDrawRoundCorner,
    RoundCorner,
    MIUIWidget,
    Circle
}