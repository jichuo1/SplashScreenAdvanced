package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout

// Elastic interactions can temporarily disable the standard ViewGroup clipping flags.
internal class NativeContentViewport(context: Context) : FrameLayout(context) {
    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
