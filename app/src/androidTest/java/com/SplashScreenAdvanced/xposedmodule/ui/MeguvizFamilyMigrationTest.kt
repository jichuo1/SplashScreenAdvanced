package com.SplashScreenAdvanced.xposedmodule.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.lumen.coacervation.engine.background.LiquidBackgroundImportResult
import com.lumen.coacervation.engine.background.LiquidBackgroundMode
import com.lumen.coacervation.engine.background.LiquidBackgroundStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeguvizFamilyMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun dockSelectionBackAndRecreationShareHomeState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(it.meguviz.isActive)
                assertNotNull(it.findViewById(R.id.native_bottom_navigation))
            }
            val iconTitle = instrumentation.targetContext.getString(R.string.icon_settings)
            device.findObject(UiSelector().description(iconTitle)).click()
            scenario.onActivity {
                assertEquals(iconTitle, it.findViewById<TextView>(R.id.native_title).text.toString())
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(it.meguviz.isActive)
                assertEquals(iconTitle, it.findViewById<TextView>(R.id.native_title).text.toString())
            }
            device.pressBack()
            scenario.onActivity {
                assertEquals(
                    instrumentation.targetContext.getString(R.string.app_name),
                    it.findViewById<TextView>(R.id.native_title).text.toString(),
                )
            }
        }
    }

    @Test fun customBackgroundImportsPrivatelyAndRestores() {
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "meguviz-background-test.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val imported = LiquidBackgroundStore.importFromUri(context, Uri.fromFile(source))
            assertTrue(imported is LiquidBackgroundImportResult.Success)
            val state = LiquidBackgroundStore.read(context)
            assertEquals(LiquidBackgroundMode.CUSTOM, state.config.mode)
            assertTrue(state.assetPresent)
            assertTrue(LiquidBackgroundStore.restoreAutomatic(context))
            assertEquals(LiquidBackgroundMode.AUTOMATIC, LiquidBackgroundStore.read(context).config.mode)
        } finally {
            source.delete()
            LiquidBackgroundStore.restoreAutomatic(context)
        }
    }

    @Test fun toolbarSearchNavigatesToRealSetting() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val search = device.findObject(UiSelector().description(
                instrumentation.targetContext.getString(R.string.settings_search)))
            assertTrue(search.waitForExists(3000))
            search.click()
            val input = device.findObject(UiSelector().resourceId(
                "${instrumentation.targetContext.packageName}:id/native_settings_search"))
            assertTrue(input.waitForExists(3000))
            input.setText("Background")
            val destination = device.findObject(UiSelector().resourceId(
                "${instrumentation.targetContext.packageName}:id/native_settings_search_results")
                .childSelector(UiSelector().text(
                    instrumentation.targetContext.getString(R.string.background_settings))))
            assertTrue(destination.waitForExists(3000))
            destination.click()
            NativeTestSupport.awaitRoute(Route.Background)
        }
    }
}
