package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import com.lumen.coacervation.engine.liquid.LiquidChoiceDrawable
import com.lumen.coacervation.engine.model.SurfaceRole
import com.lumen.coacervation.engine.widget.LumenSlider
import com.lumen.coacervation.engine.widget.LumenSliderAppearance
import com.lumen.coacervation.engine.widget.LumenSliderRange

/*
 * Engine material for the framework controls (integration standard §5.3).
 *
 * `LumenControls.style` does exactly this for SwitchCompat, CheckBox and EditText; these helpers apply the same engine
 * drawables to the framework Switch, CheckBox and EditText this module uses, so AppCompat is not pulled in just to
 * restyle them. Colours come from the palette only. Call after the engine session is prepared.
 */

private fun NativePageUi.choice(width: Int, height: Int, checkbox: Boolean = false, thumb: Boolean = false) =
    LiquidChoiceDrawable(width, height, activity.resources.displayMetrics.density, palette.surface, palette.primary,
        palette.onPrimary, palette.textPrimary, checkbox, thumb)

/** Thumb edge of the compact switch (track is twice as wide). The framework default thumb is ~26dp, which reads as oversized. */
private const val SWITCH_THUMB_DP = 21

internal fun NativePageUi.engineSwitch(view: Switch): Switch = view.apply {
    val thumb = (SWITCH_THUMB_DP * resources.displayMetrics.density).toInt()
    thumbTintList = null
    trackTintList = null
    thumbDrawable = choice(thumb, thumb, thumb = true)
    trackDrawable = choice(thumb * 2, thumb)
    splitTrack = false
    // The framework adds its own minimum width and label padding around the track; the compact size must win.
    switchMinWidth = 0
    thumbTextPadding = 0
    // A newly installed drawable starts with an empty state: push the view's current one (an already-on switch would
    // otherwise show as off until the next press).
    refreshDrawableState()
}

internal fun NativePageUi.engineCheckBox(view: CheckBox): CheckBox = view.apply {
    // Framework buttons differ per ROM (often ~32dp): clamp to a compact range.
    val density = resources.displayMetrics.density
    val size = (buttonDrawable?.intrinsicWidth ?: 0).coerceIn((20 * density).toInt(), (26 * density).toInt())
    buttonTintList = null
    buttonDrawable = choice(size, size, checkbox = true)
    refreshDrawableState()
    // The default 40dp press ripple would open a pale mask around a checked box.
    background = null
}

internal fun NativePageUi.engineEditText(view: EditText): EditText = view.apply {
    backgroundTintList = null
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    background = lumen.surface(palette.surfaceVariant, 14f, SurfaceRole.SELECTED_ITEM)
    setPadding(left, top, right, bottom)
    foreground = lumen.controlOutline(14f)
}

/** The engine's slider with the skin appearance: it follows the active material and handles stretch, magnet and haptics. */
internal fun NativePageUi.engineSlider(from: Float, to: Float, step: Float, value: Float, description: String): LumenSlider =
    LumenSlider(activity).apply {
        contentDescription = description
        configure(LumenSliderRange(from, to, step), palette, LumenSliderAppearance.Skin(lumen), value = value)
    }
