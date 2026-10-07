package com.SplashScreenAdvanced.xposedmodule.state

data class NativeUiConfig(
    val isSplitScreenEnabled: Boolean = false,
    val isBlurEnabled: Boolean = true,
    val paletteStyle: Int = 0,
)
