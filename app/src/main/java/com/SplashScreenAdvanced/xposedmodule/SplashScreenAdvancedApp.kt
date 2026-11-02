package com.SplashScreenAdvanced.xposedmodule

import android.app.Application
import com.SplashScreenAdvanced.xposedmodule.di.appModule
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemoryController
import com.SplashScreenAdvanced.xposedmodule.utils.PerfTrace
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.LumenEngineConfig
import com.lumen.coacervation.engine.LumenStorageNames
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SplashScreenAdvancedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PerfTrace.section("app.onCreate") { init() }
    }

    private fun init() {
        PerfTrace.section("app.lumen.configure") { LumenEngine.configure(LumenEngineConfig(LumenStorageNames(
            skinPreferences = "ssa_lumen_skin",
            realtimeCapturePreferences = "ssa_lumen_capture",
            backgroundPreferences = "ssa_lumen_background",
            backgroundAssetDirectory = "ssa_lumen_background_assets",
        ))) }
        PerfTrace.section("app.koin") {
            startKoin {
                androidContext(this@SplashScreenAdvancedApp)
                modules(appModule)
            }
        }
        PerfTrace.section("app.fairmemory") { FairMemoryController.install(this) }
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
