package com.SplashScreenAdvanced.xposedmodule.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.EditText
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemorySessionStore
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LumenUiRolloutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun primaryEntriesLaunchNativeActivitiesAndReturnToMain() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val routes = listOf(Route.Basic, Route.Scope, Route.Icon, Route.Bottom, Route.Background, Route.Display, Route.About)
            routes.forEach { route ->
                val title = instrumentation.targetContext.getString(pageTitleResource(route))
                val row = device.findObject(UiSelector().text(title))
                if (!row.waitForExists(1500)) device.swipe(540, 1500, 540, 500, 14)
                assertTrue("Missing entry " + title, row.waitForExists(5000))
                row.click()
                NativeTestSupport.awaitRoute(route)
                assertTrue(NativeTestSupport.resumedActivity().lumen.isPrepared)
                device.pressBack()
                NativeTestSupport.awaitRoute(Route.Main)
            }
        }
    }

    @Test fun materialDialogRecreatesTheSameNativePage() {
        ActivityScenario.launch(BasicActivity::class.java).use {
            val picker = device.findObject(UiSelector().text("Lumen material"))
            if (!picker.waitForExists(1500)) device.swipe(540, 1500, 540, 600, 14)
            assertTrue(picker.waitForExists(5000)); picker.click()
            val advanced = device.findObject(UiSelector().text("Advanced glass"))
            assertTrue(advanced.waitForExists(5000)); advanced.click()
            SystemClock.sleep(1500)
            NativeTestSupport.awaitRoute(Route.Basic)
            assertTrue(NativeTestSupport.resumedActivity().lumen.isPrepared)
            assertTrue(device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "native-basic-liquid.png")))
        }
    }

    @Test fun allSecondaryRoutesRenderAndRecreateWithoutCompose() {
        val routes = listOf(Route.CustomScope, Route.IgnoreAppIcon, Route.HideIcon, Route.RemoveBranding,
            Route.BackgroundExcept, Route.BgIndividual, Route.MinDuration, Route.ForceSplash, Route.ColorPicker(), Route.Developer)
        routes.forEach { route ->
            val intent = Intent(instrumentation.targetContext, activityForRoute(route))
            ActivityScenario.launch<NativeSettingsActivity>(intent).use { scenario ->
                SystemClock.sleep(600)
                scenario.onActivity { assertEquals(route, it.pageRoute); assertTrue(it.lumen.isPrepared) }
                scenario.recreate()
                SystemClock.sleep(450)
                scenario.onActivity { assertEquals(route, it.pageRoute); assertTrue(it.lumen.isPrepared) }
            }
        }
    }

    @Test fun applicationSearchQuerySurvivesRecreation() {
        ActivityScenario.launch(CustomScopeActivity::class.java).use { scenario ->
            SystemClock.sleep(800)
            scenario.onActivity { it.findViewById<EditText>(R.id.native_search).setText("android") }
            awaitList(scenario)
            scenario.recreate()
            awaitList(scenario)
            scenario.onActivity {
                assertEquals("android", it.findViewById<EditText>(R.id.native_search).text.toString())
                assertTrue(it.findViewById<ListView>(R.id.native_list).adapter.count > 0)
            }
        }
    }

    private fun awaitList(scenario: ActivityScenario<CustomScopeActivity>) {
        val deadline = SystemClock.uptimeMillis() + 6000
        var count = 0
        while (count == 0 && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { count = it.findViewById<ListView>(R.id.native_list).adapter.count }
            if (count == 0) SystemClock.sleep(30)
        }
        assertTrue("Search result did not finish loading", count > 0)
    }

    @Test fun rotationKeepsTheNativePageAndSession() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(600)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1200)
            scenario.onActivity { assertEquals(Route.Basic, it.pageRoute); assertTrue(it.lumen.isPrepared) }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(1200)
            scenario.onActivity { assertEquals(Route.Basic, it.pageRoute); assertTrue(it.lumen.isPrepared) }
        }
    }

    @Test fun fairMemoryTokenRestoresASecondaryNativeActivity() {
        instrumentation.runOnMainSync {
            FairMemorySessionStore.remember(Route.MinDuration)
            FairMemorySessionStore.persistForKill(instrumentation.targetContext)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            NativeTestSupport.awaitRoute(Route.MinDuration)
            device.pressBack()
            NativeTestSupport.awaitRoute(Route.Main)
        }
    }
}
