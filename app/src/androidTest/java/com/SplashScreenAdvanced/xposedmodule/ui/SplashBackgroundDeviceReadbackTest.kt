package com.SplashScreenAdvanced.xposedmodule.ui

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.manager.XposedServiceManager
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/** Opt-in readback only: never launches pages, resets settings or restarts a hooked process. */
@RunWith(AndroidJUnit4::class)
class SplashBackgroundDeviceReadbackTest {
    @Test fun readBackgroundSettingsAndLoadedHookTargets() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readDeviceSettings") == "true")
        val manager: XposedServiceManager = KoinJavaComponent.get(XposedServiceManager::class.java)
        val deadline = SystemClock.uptimeMillis() + 6000
        while (manager.currentService == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        val service = manager.currentService
        val result = JSONObject().put("apkVersion", BuildConfig.VERSION_NAME).put("serviceBound", service != null)
        if (service != null) {
            result.put("apiVersion", service.apiVersion)
            val preferences = service.getRemotePreferences(Preferences.NAME)
            result.put("backgroundMode", preferences.getInt(Preferences.Background.CHANG_BG_COLOR_TYPE.name, 0))
                .put("backgroundColorMode", preferences.getInt(Preferences.Background.BG_COLOR_MODE.name, 0))
                .put("skipAppsWithOwnColor", preferences.getBoolean(Preferences.Background.SKIP_APP_WITH_BG_COLOR.name, true))
                .put("dayColor", preferences.getString(Preferences.Background.OVERALL_BG_COLOR.name, "#FFFFFF"))
                .put("nightColor", preferences.getString(Preferences.Background.OVERALL_BG_COLOR_NIGHT.name, "#000000"))
                .put("minimumDuration", preferences.getInt(Preferences.Display.MIN_DURATION.name, 0))
            if (service.apiVersion >= 102) result.put("targets", JSONArray().apply {
                runCatching { service.runningTargets }.getOrNull()?.forEach { target ->
                    put(JSONObject().put("process", target.processName).put("state", target.state.toString()))
                }
            })
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "splash-background-device-readback.json").writeText(result.toString(2))
    }
}
