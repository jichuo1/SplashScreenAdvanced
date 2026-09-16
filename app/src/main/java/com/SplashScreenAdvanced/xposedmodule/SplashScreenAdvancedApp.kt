package com.SplashScreenAdvanced.xposedmodule

import android.app.Application
import com.SplashScreenAdvanced.xposedmodule.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SplashScreenAdvancedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SplashScreenAdvancedApp)
            modules(appModule)
        }
    }
}
