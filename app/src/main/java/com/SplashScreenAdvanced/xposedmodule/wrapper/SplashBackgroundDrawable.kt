package com.SplashScreenAdvanced.xposedmodule.wrapper

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

/** OEM previews may be transparent or cover only part of the splash; keep a full color behind them. */
internal fun splashBackgroundWithPreview(color: Int, preview: Drawable): Drawable =
    LayerDrawable(arrayOf(ColorDrawable(color), preview))
