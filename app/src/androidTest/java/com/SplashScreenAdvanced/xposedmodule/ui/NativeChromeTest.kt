package com.SplashScreenAdvanced.xposedmodule.ui

import android.graphics.Color
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.BasicActivity
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeChromeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun scrollingContentCannotPaintOverTheTitleBar() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            val titlePosition = IntArray(2)
            var x = 0
            var y = 0
            scenario.onActivity { activity ->
                val title = activity.findViewById<TextView>(R.id.native_title)
                title.getLocationOnScreen(titlePosition)
                x = activity.resources.displayMetrics.widthPixels - activity.dp(32)
                y = titlePosition[1] + title.height / 2
                val scroll = activity.findViewById<ScrollView>(R.id.native_scroll)
                val content = scroll.getChildAt(0) as LinearLayout
                content.removeAllViews()
                repeat(3) {
                    content.addView(View(activity).apply { setBackgroundColor(Color.MAGENTA) },
                        LinearLayout.LayoutParams(-1, activity.dp(600)))
                }
                content.requestLayout()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(150)
            scenario.onActivity { it.findViewById<ScrollView>(R.id.native_scroll).scrollTo(0, it.dp(240)) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
            val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val sample = image.getPixel(x, y)
                assertFalse("Scrolled content covers title chrome", Color.red(sample) > 245 &&
                    Color.green(sample) < 10 && Color.blue(sample) > 245)
            } finally { image.recycle() }
            scenario.onActivity {
                val after = IntArray(2)
                it.findViewById<TextView>(R.id.native_title).getLocationOnScreen(after)
                assertArrayEquals("Title moved with page scrolling", titlePosition, after)
            }
        }
    }

    @Test fun titleMaterialCoversTheStatusBarWithoutMovingItsControlsUnderIcons() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            scenario.onActivity { activity ->
                val title = activity.findViewById<TextView>(R.id.native_title)
                val row = title.parent as ViewGroup
                val material = if (row.background != null) row else row.parent as ViewGroup
                val position = IntArray(2); material.getLocationOnScreen(position)
                assertEquals("Material stops below status bar", 0, position[1])
                val titleLocation = IntArray(2); title.getLocationOnScreen(titleLocation)
                val statusTop = activity.window.decorView.rootWindowInsets
                    .getInsets(android.view.WindowInsets.Type.statusBars()).top
                assertTrue("Title overlaps system status icons", titleLocation[1] >= statusTop)
            }
        }
    }

    @Test fun advancedMaterialRealPageKeepsItsHeaderAfterScrolling() {
        instrumentation.runOnMainSync {
            assertTrue(LumenEngine.selectMaterial(instrumentation.targetContext, SkinId.LIQUID, true))
        }
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(1000)
            val before = IntArray(2)
            scenario.onActivity { it.findViewById<TextView>(R.id.native_title).getLocationOnScreen(before) }
            capture("chrome-initial.png")
            scenario.onActivity { it.findViewById<ScrollView>(R.id.native_scroll).smoothScrollTo(0, it.dp(450)) }
            SystemClock.sleep(550)
            scenario.onActivity {
                val after = IntArray(2)
                it.findViewById<TextView>(R.id.native_title).getLocationOnScreen(after)
                assertArrayEquals(before, after)
                val material = it.findViewById<View>(R.id.native_chrome)
                material.getLocationOnScreen(after)
                assertEquals(0, after[1])
            }
            capture("chrome-scrolled.png")
        }
    }

    private fun capture(name: String) {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }
}
