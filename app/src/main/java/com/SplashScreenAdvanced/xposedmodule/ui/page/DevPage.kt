package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.engineSlider
import com.lumen.coacervation.engine.widget.LumenSlider
import com.lumen.coacervation.engine.widget.LumenSliderSlideListener

fun NativePageUi.buildDevPage(): View = scrollContent {
    addSetting(switch(R.string.dev_settings, Preferences.Dev.ENABLE_DEV_SETTINGS) {
        activity.uiState.syncDevMode(); activity.requestBack()
    })
    val card = column().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = activity.meguviz.own(lumen.cardBackground(palette.surface, 16f))
        // The slider's press growth and end stretch draw outside its bounds (standard §12.3).
        clipChildren = false
        clipToPadding = false
    }
    val value = text("")
    card.addView(text(string(R.string.dev_icon_round_corner_rate)))
    card.addView(value)
    // The engine's slider; the preference is written when the finger lifts.
    val slider = engineSlider(0f, 50f, 1f, repo.get(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE).coerceIn(0, 50).toFloat(),
        string(R.string.dev_icon_round_corner_rate)).apply {
        setOnValueChangeListener { _, progress, _ -> value.text = progress.toInt().toString() + "% / 50%" }
        setOnSlideListener(object : LumenSliderSlideListener {
            override fun onSlideFinished(slider: LumenSlider, value: Float) { write(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE, value.toInt()) }
        })
    }
    card.addView(slider)
    value.setOnClickListener { numberDialog(string(R.string.dev_icon_round_corner_rate), null, slider.value.toInt(), max = 50) {
        if (it != null && write(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE, it)) { slider.setValue(it.toFloat(), animate = false); refresh() }
    } }
    bind {
        val current = repo.get(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE).coerceIn(0, 50)
        slider.setValue(current.toFloat(), animate = false); value.text = current.toString() + "% / 50%"
    }
    addSetting(card)
}
