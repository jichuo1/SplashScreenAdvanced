package com.SplashScreenAdvanced.xposedmodule.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.AboutActivity
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LumenBridgePocTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun softMaterialUsesOnlyNativeViews() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(700)
            scenario.onActivity { activity ->
                assertTrue(activity.lumen.isPrepared)
                assertNoCompose(activity.window.decorView)
                val rows = descendants(activity.window.decorView).filterIsInstance<NativeRow>()
                assertTrue(rows.size >= 8)
                assertTrue(rows.all { it.background != null })
            }
        }
    }

    @Test fun nativeRowHeldDragMovesAndRestoresWithoutOpeningThePage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(700)
            lateinit var row: NativeRow
            val before = IntArray(2)
            scenario.onActivity { activity ->
                row = descendants(activity.window.decorView).filterIsInstance<NativeRow>()
                    .first { it.titleView.text.toString() == "Basic settings" }
                row.getLocationOnScreen(before)
            }
            val down = SystemClock.uptimeMillis()
            val x = before[0] + row.width / 2f
            val y = before[1] + row.height / 2f
            send(down, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(220)
            send(down, MotionEvent.ACTION_MOVE, x + 42f, y)
            SystemClock.sleep(70)
            scenario.onActivity {
                val moved = IntArray(2); row.getLocationOnScreen(moved)
                assertTrue(abs(moved[0] - before[0]) > 1)
            }
            send(down, MotionEvent.ACTION_UP, x + 42f, y)
            SystemClock.sleep(1200)
            scenario.onActivity {
                val restored = IntArray(2); row.getLocationOnScreen(restored)
                assertTrue(abs(restored[0] - before[0]) <= 1)
                assertTrue(abs(restored[1] - before[1]) <= 1)
            }
            assertTrue(NativeTestSupport.resumedActivity() is MainActivity)
        }
    }

    @Test fun advancedMaterialSurvivesRecreation() {
        instrumentation.runOnMainSync { assertTrue(LumenEngine.selectMaterial(instrumentation.targetContext, SkinId.LIQUID, true)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(1600)
            scenario.onActivity {
                assertTrue(it.lumen.isPrepared)
                assertTrue(it.lumen.isLiquidEffective)
                assertNotNull(it.lumen.backendName)
            }
            scenario.recreate()
            SystemClock.sleep(1000)
            scenario.onActivity { assertTrue(it.lumen.isPrepared); assertNoCompose(it.window.decorView) }
        }
    }

    @Test fun nativeSwitchKeepsItsOwnGestureWithoutMovingItsRow() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            SystemClock.sleep(700)
            lateinit var toggle: Switch
            lateinit var row: NativeRow
            val before = IntArray(2)
            val location = IntArray(2)
            scenario.onActivity {
                row = descendants(it.window.decorView).filterIsInstance<NativeRow>()
                    .first { item -> descendants(item).any { child -> child is Switch } }
                toggle = descendants(row).filterIsInstance<Switch>().first()
                row.getLocationOnScreen(before); toggle.getLocationOnScreen(location)
            }
            val down = SystemClock.uptimeMillis()
            val x = location[0] + toggle.width / 2f
            val y = location[1] + toggle.height / 2f
            send(down, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(220)
            send(down, MotionEvent.ACTION_MOVE, x + 35f, y)
            SystemClock.sleep(70)
            scenario.onActivity {
                val after = IntArray(2); row.getLocationOnScreen(after)
                assertArrayEquals(before, after)
            }
            send(down, MotionEvent.ACTION_CANCEL, x + 35f, y)
        }
    }

    private fun send(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
    }

    private fun assertNoCompose(root: View) {
        assertFalse(descendants(root).any { it.javaClass.name.startsWith("androidx.compose.") })
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
