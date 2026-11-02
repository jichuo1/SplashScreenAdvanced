package com.SplashScreenAdvanced.xposedmodule.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsTab
import com.lumen.coacervation.engine.motion.pager.LumenPagePager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The dock pages that are not on screen are built ahead, one per idle frame, so a swipe never builds one. */
@RunWith(AndroidJUnit4::class)
class DockPagePrewarmTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun everyDockPageIsBuiltWithoutTheUserVisitingIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var pager: LumenPagePager? = null
            scenario.onActivity { pager = descendants(it.window.decorView).filterIsInstance<LumenPagePager>().single() }
            val hosts = checkNotNull(pager)
            fun built() = (0 until hosts.childCount).count { (hosts.getChildAt(it) as ViewGroup).childCount > 0 }

            val deadline = SystemClock.uptimeMillis() + 10_000
            var count = 0
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { count = built() }
                if (count == SettingsTab.entries.size) break
                SystemClock.sleep(100)
            }
            assertEquals("Dock pages built while idle", SettingsTab.entries.size, count)
            assertEquals(SettingsTab.entries.size, hosts.childCount)
            // The selected page is still the one the user opened with.
            instrumentation.runOnMainSync { assertEquals(0, hosts.selectedPage) }
            assertTrue(hosts.isSettled)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
