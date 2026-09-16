package com.SplashScreenAdvanced.xposedmodule.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.SplashScreenAdvanced.xposedmodule.state.GlobalUIViewModel

val LocalAppUiState = staticCompositionLocalOf<GlobalUIViewModel> {
    error("LocalAppUiState is not provided")
}
