package com.SplashScreenAdvanced.xposedmodule.state

import androidx.lifecycle.ViewModel
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository

class GlobalUIViewModel(
    private val repo: GlobalPreferencesRepository
) : ViewModel() {
    val configFlow = repo.uiConfigFlow
}
