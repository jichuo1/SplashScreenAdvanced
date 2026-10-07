package com.SplashScreenAdvanced.xposedmodule.ui

import android.app.LocaleManager
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeSettingsActivity
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import org.junit.Assert.assertEquals
import org.koin.java.KoinJavaComponent

internal object NativeTestSupport {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun prepare() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED, Stage.CREATED).flatMap {
                monitor.getActivitiesInStage(it)
            }.distinct().filterIsInstance<NativeSettingsActivity>().forEach { it.finish() }
            val context = instrumentation.targetContext
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
            val repo: GlobalPreferencesRepository = KoinJavaComponent.get(GlobalPreferencesRepository::class.java)
            repo.update(Preferences.Module.AUTO_UPDATE_CHECK, false)
            repo.update(Preferences.Module.SPLIT_VIEW, false)
            repo.update(Preferences.Module.UI_STYLE, 0)
            context.getSharedPreferences("fair_memory_session", 0).edit().putBoolean("pending_restore", false).commit()
            LumenEngine.selectMaterial(context, SkinId.MATERIAL_YOU)
        }
        SystemClock.sleep(200)
    }

    fun resumedActivity(): NativeSettingsActivity {
        var result: NativeSettingsActivity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<NativeSettingsActivity>().lastOrNull()
        }
        return checkNotNull(result)
    }

    fun awaitRoute(route: Route) {
        val deadline = SystemClock.uptimeMillis() + 8000
        var actual: Route? = null
        while (SystemClock.uptimeMillis() < deadline) {
            actual = runCatching { resumedActivity().pageRoute }.getOrNull()
            if (actual == route) { SystemClock.sleep(600); return }
            SystemClock.sleep(25)
        }
        assertEquals("Foreground native Activity", route, actual)
    }
}
