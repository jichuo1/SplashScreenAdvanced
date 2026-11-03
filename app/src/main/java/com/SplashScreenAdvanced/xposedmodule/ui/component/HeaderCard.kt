package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.header(imageResource: Int, title: String): View = FrameLayout(activity).apply {
    background = lumen.cardBackground(palette.surfaceVariant, 20f)
    addView(ImageView(activity).apply {
        setImageResource(imageResource); scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, FrameLayout.LayoutParams(dp(128), dp(108), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(8) })
    // 128dp: the banner introduces the page without pushing the first group below the fold.
    addView(text(title, 24f, bold = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(22), 0, dp(22), 0)
    }, FrameLayout.LayoutParams(-1, dp(128)))
}
