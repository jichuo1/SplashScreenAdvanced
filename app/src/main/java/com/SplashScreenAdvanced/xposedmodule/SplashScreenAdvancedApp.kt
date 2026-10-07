package com.SplashScreenAdvanced.xposedmodule

import android.app.Application
import com.SplashScreenAdvanced.xposedmodule.di.appModule
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemoryController
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.LumenEngineConfig
import com.lumen.coacervation.engine.LumenStorageNames
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SplashScreenAdvancedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LumenEngine.configure(LumenEngineConfig(LumenStorageNames(
            skinPreferences = "ssa_lumen_skin",
            realtimeCapturePreferences = "ssa_lumen_capture",
            backgroundPreferences = "ssa_lumen_background",
            backgroundAssetDirectory = "ssa_lumen_background_assets",
        )))
        startKoin {
            androidContext(this@SplashScreenAdvancedApp)
            modules(appModule)
        }
        FairMemoryController.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FairMemoryController.onAndroidTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
            LumenEngine.releaseGraphics()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FairMemoryController.trimLocalCaches("onLowMemory")
        LumenEngine.releaseGraphics()
    }
}
