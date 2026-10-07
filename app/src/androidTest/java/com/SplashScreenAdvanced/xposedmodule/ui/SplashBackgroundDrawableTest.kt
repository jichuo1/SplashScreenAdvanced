package com.SplashScreenAdvanced.xposedmodule.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.hook.utils.SplashBackgroundRegistry
import com.SplashScreenAdvanced.xposedmodule.wrapper.splashBackgroundWithPreview
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashBackgroundDrawableTest {
    @Test fun transparentOemPreviewCannotExposeTheApplicationBehindTheSplash() {
        val preview = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val drawable = BitmapDrawable(InstrumentationRegistry.getInstrumentation().targetContext.resources, preview)
            assertEquals("Baseline transparent preview exposes the underlying app", Color.MAGENTA, render(drawable))
            assertEquals(Color.WHITE, render(splashBackgroundWithPreview(Color.WHITE, drawable)))
            assertEquals(Color.BLACK, render(splashBackgroundWithPreview(Color.BLACK, drawable)))
        } finally { preview.recycle() }
    }

    @Test fun opaqueOemBackgroundRetainsItsActualColor() {
        assertEquals(Color.GREEN, render(splashBackgroundWithPreview(Color.WHITE, ColorDrawable(Color.GREEN))))
    }

    @Test fun translucentOemBackgroundBlendsWithTheSplashColorRatherThanUnderlyingContent() {
        val result = render(splashBackgroundWithPreview(Color.WHITE, ColorDrawable(0x800000ff.toInt())))
        assertTrue(Color.red(result) in 126..128)
        assertTrue(Color.green(result) in 126..128)
        assertEquals(255, Color.blue(result))
        assertEquals(255, Color.alpha(result))
    }

    @Test fun partlyCoveredOemPreviewKeepsItsImageAndFillsTheRestOfTheWindow() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(8, 8, Color.GREEN)
        try {
            val preview = BitmapDrawable(InstrumentationRegistry.getInstrumentation().targetContext.resources, bitmap)
            val background = splashBackgroundWithPreview(Color.WHITE, preview)
            assertEquals(Color.WHITE, render(background, 1, 1))
            assertEquals(Color.GREEN, render(background, 8, 8))
        } finally { bitmap.recycle() }
    }

    @Test fun normalizedCustomColorRemainsOpaqueEvenForAnOldAlphaBearingPreference() {
        assertEquals(0xff123456.toInt(), render(ColorDrawable(SplashBackgroundRegistry.opaque(0x00123456))))
    }

    private fun render(drawable: Drawable, x: Int = 1, y: Int = 1): Int {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.MAGENTA)
            drawable.setBounds(0, 0, 16, 16)
            drawable.draw(canvas)
            return bitmap.getPixel(x, y)
        } finally { bitmap.recycle() }
    }
}
