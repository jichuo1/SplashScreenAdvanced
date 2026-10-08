package com.SplashScreenAdvanced.xposedmodule.ui

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.BackgroundExceptActivity
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

    @Test fun applicationListCannotPaintOverItsFixedControls() {
        assertApplicationListClipping(SkinId.MATERIAL_YOU)
    }

    @Test fun liquidApplicationListCannotPaintOverItsFixedControls() {
        assertApplicationListClipping(SkinId.LIQUID)
    }

    @Test fun liquidApplicationListControlsRemainUsableAfterScrollingAndFiltering() {
        instrumentation.runOnMainSync {
            assertTrue(LumenEngine.selectMaterial(instrumentation.targetContext, SkinId.LIQUID, true))
        }
        ActivityScenario.launch(BackgroundExceptActivity::class.java).use { scenario ->
            awaitApplicationList(scenario) { it.count > 0 }
            SystemClock.sleep(650)
            val bounds = Rect()
            val searchPosition = IntArray(2)
            val savePosition = IntArray(2)
            var count = 0
            scenario.onActivity {
                assertTrue(it.lumen.isLiquidEffective)
                val list = it.findViewById<ListView>(R.id.native_list)
                list.getGlobalVisibleRect(bounds)
                count = list.count
                it.findViewById<View>(R.id.native_search).getLocationOnScreen(searchPosition)
                it.findViewById<View>(R.id.native_save).getLocationOnScreen(savePosition)
            }
            capture("application-list-liquid-initial.png")
            val device = UiDevice.getInstance(instrumentation)
            repeat(3) { device.swipe(bounds.centerX(), bounds.bottom - 40, bounds.centerX(), bounds.top + 40, 12) }
            SystemClock.sleep(400)
            scenario.onActivity {
                assertTrue(it.findViewById<ListView>(R.id.native_list).firstVisiblePosition > 0)
                val after = IntArray(2)
                it.findViewById<View>(R.id.native_search).getLocationOnScreen(after)
                assertArrayEquals(searchPosition, after)
                it.findViewById<View>(R.id.native_save).getLocationOnScreen(after)
                assertArrayEquals(savePosition, after)
            }
            capture("application-list-liquid-scrolled.png")
            val search = device.findObject(UiSelector().resourceId("${instrumentation.targetContext.packageName}:id/native_search"))
            search.setText("no.such.package.scroll.regression")
            awaitApplicationList(scenario) { it.count == 0 }
            search.setText("android")
            awaitApplicationList(scenario) { it.count > 0 }
            scenario.onActivity { assertEquals("android", it.findViewById<EditText>(R.id.native_search).text.toString()) }
            search.clearTextField()
            awaitApplicationList(scenario) { it.count == count }
            device.findObject(UiSelector().resourceId("${instrumentation.targetContext.packageName}:id/native_save")).click()
            scenario.onActivity { assertFalse(it.pageUi.hasUnsavedChanges) }
        }
    }

    private fun awaitApplicationList(scenario: ActivityScenario<BackgroundExceptActivity>, ready: (ListView) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        var loaded = false
        while (!loaded && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { loaded = ready(it.findViewById(R.id.native_list)) }
            if (!loaded) SystemClock.sleep(30)
        }
        assertTrue("Application list did not finish updating", loaded)
    }

    private fun assertApplicationListClipping(skin: SkinId) {
        instrumentation.runOnMainSync {
            assertTrue(LumenEngine.selectMaterial(instrumentation.targetContext, skin, true))
        }
        ActivityScenario.launch(BackgroundExceptActivity::class.java).use { scenario ->
            awaitApplicationList(scenario) { it.count > 0 }
            SystemClock.sleep(650)
            val controls = listOf(R.id.native_search, R.id.native_save)
            val positions = controls.associateWith { IntArray(2) }
            val samples = mutableListOf<Pair<Int, Int>>()
            lateinit var list: ListView
            scenario.onActivity { activity ->
                list = activity.findViewById(R.id.native_list)
                controls.forEach { id ->
                    val control = activity.findViewById<View>(id)
                    control.getLocationOnScreen(positions.getValue(id))
                    val position = positions.getValue(id)
                    samples += position[0] + control.width / 2 to position[1] + control.height / 2
                }
                list.adapter = object : BaseAdapter() {
                    override fun getCount() = 40
                    override fun getItem(position: Int) = position
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                        convertView ?: View(activity).apply {
                            setBackgroundColor(Color.MAGENTA)
                            layoutParams = AbsListView.LayoutParams(-1, activity.dp(240))
                        }
                }
                // Elastic drags temporarily disable ancestor clipping until their rebound finishes.
                var ancestor: View? = list
                while (ancestor is ViewGroup) {
                    ancestor.clipChildren = false
                    ancestor.clipToPadding = false
                    ancestor = ancestor.parent as? View
                }
            }
            instrumentation.waitForIdleSync()
            listOf(8, 16, 5).forEach { position ->
                scenario.onActivity { list.setSelectionFromTop(position, -it.dp(200)) }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(200)
                val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    samples.forEach { (x, y) ->
                        val sample = image.getPixel(x, y)
                        assertFalse("Application row covers fixed controls ($skin, row $position)",
                            Color.red(sample) > 245 && Color.green(sample) < 10 && Color.blue(sample) > 245)
                    }
                    scenario.onActivity {
                        assertEquals(position, list.firstVisiblePosition)
                        val location = IntArray(2)
                        list.getLocationOnScreen(location)
                        assertTrue("First row must straddle the list's top edge", list.getChildAt(0).top < 0)
                        assertEquals("The visible part of the row must still render", Color.MAGENTA,
                            image.getPixel(location[0] + list.width / 2, location[1] + it.dp(16)))
                        controls.forEach { id ->
                            val after = IntArray(2)
                            it.findViewById<View>(id).getLocationOnScreen(after)
                            assertArrayEquals("Fixed control moved while scrolling", positions.getValue(id), after)
                        }
                    }
                } finally { image.recycle() }
            }
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
