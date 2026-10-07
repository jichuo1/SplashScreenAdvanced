package com.SplashScreenAdvanced.xposedmodule.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.BasicActivity
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import com.lumen.coacervation.engine.motion.morph.ContainerMorphHost
import com.lumen.coacervation.engine.motion.morph.ContainerMorphOrigin
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Compares the actual native title trajectory with its measured settled endpoint. */
@RunWith(AndroidJUnit4::class)
class TitleMotionHandoffTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() { NativeTestSupport.prepare() }
    @Test fun softMaterialNativeTitleHandsOffAtTheMeasuredTextPosition() = verify(SkinId.MATERIAL_YOU)
    @Test fun advancedMaterialNativeTitleHandsOffAtTheMeasuredTextPosition() = verify(SkinId.LIQUID)

    private fun verify(skin: SkinId) {
        instrumentation.runOnMainSync { assertTrue(LumenEngine.selectMaterial(instrumentation.targetContext, skin, skin == SkinId.LIQUID)) }
        val application = instrumentation.targetContext.applicationContext as Application
        var measured: Sample? = null
        var detail: BasicActivity? = null
        var observer: ViewTreeObserver? = null
        var listener: ViewTreeObserver.OnPreDrawListener? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                if (activity !is BasicActivity) return
                detail = activity
                val host = descendants(activity.window.decorView).filterIsInstance<ContainerMorphHost>().first()
                val flying = descendants(host).filterIsInstance<TextView>().first { it.parent === host }
                val location = IntArray(2)
                listener = ViewTreeObserver.OnPreDrawListener {
                    if (flying.visibility == View.VISIBLE && flying.alpha > .9f && host.expansion in .5f..1f) {
                        flying.getLocationOnScreen(location)
                        val origin = checkNotNull(ContainerMorphOrigin.from(activity.intent)).titleBoundsOnScreen
                        val p = host.expansion
                        // Remove the remaining interpolation distance; rounding is allowed by two physical pixels.
                        val x = (location[0] + flying.totalPaddingLeft - (1f - p) * origin.left) / p
                        val y = (location[1] + flying.totalPaddingTop - (1f - p) * origin.top) / p
                        measured = Sample(p, x, y)
                    }
                    true
                }
                observer = host.viewTreeObserver
                observer?.addOnPreDrawListener(listener)
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        instrumentation.runOnMainSync { application.registerActivityLifecycleCallbacks(callbacks) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                SystemClock.sleep(1200)
                scenario.onActivity {
                    descendants(it.window.decorView).filterIsInstance<NativeRow>()
                        .first { row -> row.titleView.text.toString() == "Basic settings" }.performClick()
                }
                NativeTestSupport.awaitRoute(Route.Basic)
                val deadline = SystemClock.uptimeMillis() + 6000
                var expanded = false
                while (!expanded && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        expanded = detail?.findViewById<TextView>(R.id.native_title)?.alpha == 1f
                    }
                    if (!expanded) SystemClock.sleep(25)
                }
                assertTrue("Native title never became visible", expanded)
                instrumentation.runOnMainSync {
                    val sample = checkNotNull(measured) { "No native flying-title endpoint was drawn" }
                    val title = checkNotNull(detail).findViewById<TextView>(R.id.native_title)
                    val location = IntArray(2); title.getLocationOnScreen(location)
                    val x = location[0] + title.totalPaddingLeft + title.layout.getLineLeft(0)
                    val y = location[1] + title.totalPaddingTop
                    assertTrue("X handoff: " + sample + " target=" + x, abs(sample.x - x) <= 2f)
                    assertTrue("Y handoff: " + sample + " target=" + y, abs(sample.y - y) <= 2f)
                    assertEquals(1f, title.alpha, 0f)
                }
                instrumentation.runOnMainSync { detail?.requestBack() }
                NativeTestSupport.awaitRoute(Route.Main)
            }
        } finally {
            instrumentation.runOnMainSync {
                listener?.let { if (observer?.isAlive == true) observer?.removeOnPreDrawListener(it) }
                application.unregisterActivityLifecycleCallbacks(callbacks)
                detail?.finish()
            }
        }
    }

    private data class Sample(val progress: Float, val x: Float, val y: Float)
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
