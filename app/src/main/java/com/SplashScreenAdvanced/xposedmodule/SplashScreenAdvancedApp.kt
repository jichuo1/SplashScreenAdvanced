package com.SplashScreenAdvanced.xposedmodule

import android.app.Application
import com.SplashScreenAdvanced.xposedmodule.di.appModule
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemoryController
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SplashScreenAdvancedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SplashScreenAdvancedApp)
            modules(appModule)
        }
        FairMemoryController.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FairMemoryController.onAndroidTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FairMemoryController.trimLocalCaches("onLowMemory")
    }
}
