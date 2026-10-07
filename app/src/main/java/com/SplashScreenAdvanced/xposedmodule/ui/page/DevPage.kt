package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.view.View
import android.widget.SeekBar
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildDevPage(): View = scrollContent {
    addSetting(switch(R.string.dev_settings, Preferences.Dev.ENABLE_DEV_SETTINGS) {
        activity.uiState.syncDevMode(); activity.requestBack()
    })
    val card = column().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = lumen.cardBackground(palette.surface, 16f)
    }
    val value = text("")
    card.addView(text(string(R.string.dev_icon_round_corner_rate)))
    card.addView(value)
    val slider = SeekBar(activity).apply {
        max = 50
        progress = repo.get(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE).coerceIn(0, 50)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { value.text = progress.toString() + "% / 50%" }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) { write(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE, bar.progress) }
        })
    }
    card.addView(slider)
    value.setOnClickListener { numberDialog(string(R.string.dev_icon_round_corner_rate), null, slider.progress, max = 50) {
        if (it != null && write(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE, it)) { slider.progress = it; refresh() }
    } }
    bind {
        val current = repo.get(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE).coerceIn(0, 50)
        slider.progress = current; value.text = current.toString() + "% / 50%"
    }
    addSetting(card)
}
