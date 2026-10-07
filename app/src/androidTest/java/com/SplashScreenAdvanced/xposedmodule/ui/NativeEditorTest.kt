package com.SplashScreenAdvanced.xposedmodule.ui

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.BasicActivity
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.ColorPickerActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

@RunWith(AndroidJUnit4::class)
class NativeEditorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun hueOnWhiteIsRetainedAndColorDraftSurvivesRecreation() {
        instrumentation.runOnMainSync {
            val repo: GlobalPreferencesRepository = KoinJavaComponent.get(GlobalPreferencesRepository::class.java)
            repo.update(Preferences.Background.OVERALL_BG_COLOR, "#FFFFFF")
            repo.update(Preferences.Background.OVERALL_BG_COLOR_NIGHT, "#FFFFFF")
        }
        ActivityScenario.launch(ColorPickerActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 6000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ready = it.pageUi.saveState().containsKey("color") }
                if (!ready) SystemClock.sleep(30)
            }
            assertTrue("Color editor did not load", ready)
            lateinit var hue: SeekBar
            val location = IntArray(2)
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).filterIsInstance<Button>()
                    .first { it.text.toString() == activity.getString(R.string.hsv_color_space) }.performClick()
                descendants(activity.window.decorView).filterIsInstance<ScrollView>().first().fullScroll(View.FOCUS_DOWN)
            }
            SystemClock.sleep(200)
            scenario.onActivity { activity ->
                hue = descendants(activity.window.decorView).filterIsInstance<SeekBar>().first { bar ->
                    descendants(bar.parent as View).filterIsInstance<TextView>().any {
                        it.text.toString().startsWith(activity.getString(R.string.hue))
                    }
                }
                hue.requestRectangleOnScreen(Rect(0, 0, hue.width, hue.height), true)
            }
            SystemClock.sleep(250)
            scenario.onActivity { hue.getLocationOnScreen(location) }
            assertTrue("Hue slider is outside the screen: " + location[1], location[1] >= 0 &&
                location[1] + hue.height / 2 < UiDevice.getInstance(instrumentation).displayHeight)
            UiDevice.getInstance(instrumentation).click(location[0] + (hue.width * .65f).toInt(), location[1] + hue.height / 2)
            SystemClock.sleep(120)
            var selectedHue = 0f
            scenario.onActivity {
                val state = it.pageUi.saveState()
                assertEquals(Color.WHITE, state.getInt("color"))
                selectedHue = state.getFloatArray("colorHsv")!![0]
                assertTrue("Hue was lost on white", selectedHue > 100f)
            }
            scenario.recreate()
            SystemClock.sleep(900)
            scenario.onActivity {
                val state = it.pageUi.saveState()
                assertEquals(Color.WHITE, state.getInt("color"))
                assertEquals(selectedHue, state.getFloatArray("colorHsv")!![0], .001f)
            }
        }
    }

    @Test fun nativeSplitLayoutUsesSeparateMasterAndDetailScrollState() {
        instrumentation.runOnMainSync {
            val repo: GlobalPreferencesRepository = KoinJavaComponent.get(GlobalPreferencesRepository::class.java)
            repo.update(Preferences.Module.SPLIT_VIEW, true)
        }
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(1400)
            scenario.onActivity {
                val master = it.findViewById<ScrollView>(R.id.native_master_scroll)
                val detail = it.findViewById<ScrollView>(R.id.native_scroll)
                assertNotNull(master); assertNotNull(detail); assertNotSame(master, detail)
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(900)
        }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
