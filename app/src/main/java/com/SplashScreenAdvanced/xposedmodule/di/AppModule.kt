package com.SplashScreenAdvanced.xposedmodule.di

import com.SplashScreenAdvanced.xposedmodule.manager.XposedServiceManager
import com.SplashScreenAdvanced.xposedmodule.provider.AppPreferenceActions
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.state.GlobalUIViewModel
import com.SplashScreenAdvanced.xposedmodule.utils.RemotePreferenceStore
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::XposedServiceManager) { createdAtStart() }

    singleOf(::RemotePreferenceStore)

    singleOf(::GlobalPreferencesRepository) { createdAtStart() }
    singleOf(::AppPreferenceActions)

    viewModelOf(::GlobalUIViewModel)
}
