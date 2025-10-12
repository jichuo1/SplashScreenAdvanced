package com.gswxxn.restoresplashscreen.data

import com.gswxxn.restoresplashscreen.data.RoundDegree.Circle
import com.gswxxn.restoresplashscreen.data.RoundDegree.MIUIWidget
import com.gswxxn.restoresplashscreen.data.RoundDegree.NotDrawRoundCorner
import com.gswxxn.restoresplashscreen.data.RoundDegree.RoundCorner


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