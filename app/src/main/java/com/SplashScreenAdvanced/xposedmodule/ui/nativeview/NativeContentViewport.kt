package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout

/** Scroll/elastic children may draw into their margins, but never into window chrome. */
internal class NativeContentViewport(context: Context) : FrameLayout(context) {
    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
