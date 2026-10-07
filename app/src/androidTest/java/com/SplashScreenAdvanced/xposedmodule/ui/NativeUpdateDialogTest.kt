package com.SplashScreenAdvanced.xposedmodule.ui

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.BasicActivity
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.showUpdateDialog
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker.ReleaseInfo
import com.lumen.coacervation.engine.LumenEngine
import com.lumen.coacervation.engine.model.SkinId
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeUpdateDialogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() { NativeTestSupport.prepare() }

    @Test fun longFormattedAnnouncementScrollsWithoutMovingOrHidingActions() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            var dialog: Dialog? = null
            var opened = 0
            var dismissed = 0
            scenario.onActivity { activity ->
                dialog = activity.pageUi.showUpdateDialog(release(longNotes), { dismissed++ }, { opened++ })
            }
            settle()
            val modal = checkNotNull(dialog)
            instrumentation.runOnMainSync {
                assertActionsVisible(modal)
                val views = descendants(checkNotNull(modal.window).decorView)
                assertTrue("Table is rendered natively", views.any { it is TableLayout })
                val spans = views.filterIsInstance<TextView>().mapNotNull { it.text as? Spanned }
                assertTrue("Headings and emphasis are formatted", spans.any { it.getSpans(0, it.length, StyleSpan::class.java).isNotEmpty() })
                assertTrue("Release links are clickable", spans.any { it.getSpans(0, it.length, ClickableSpan::class.java).isNotEmpty() })
                assertTrue("Content beyond the old 2000-character cutoff survives", views.filterIsInstance<TextView>().any { it.text.contains("END_OF_ANNOUNCEMENT") })
                assertFalse(views.filterIsInstance<TextView>().any { it.text.contains("<div") || it.text.contains("**修复**") })
            }
            capture("update-announcement.png")
            val before = IntArray(2)
            instrumentation.runOnMainSync {
                modal.findViewById<View>(R.id.native_update_action).getLocationOnScreen(before)
                modal.findViewById<ScrollView>(R.id.native_update_notes).fullScroll(View.FOCUS_DOWN)
            }
            settle()
            instrumentation.runOnMainSync {
                val after = IntArray(2)
                modal.findViewById<View>(R.id.native_update_action).getLocationOnScreen(after)
                assertArrayEquals("Update action scrolls with announcement", before, after)
                assertActionsVisible(modal)
                assertTrue(modal.findViewById<ScrollView>(R.id.native_update_notes).scrollY > 0)
                modal.findViewById<View>(R.id.native_update_action).performClick()
                modal.findViewById<View>(R.id.native_update_action).performClick()
            }
            settle()
            assertEquals("Open release exactly once", 1, opened)
            assertEquals(1, dismissed)
            assertFalse(modal.isShowing)
        }
    }

    @Test fun emptyAnnouncementStillOffersUpdateAndCancel() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            var dialog: Dialog? = null
            var opened = 0
            var dismissed = 0
            scenario.onActivity { dialog = it.pageUi.showUpdateDialog(release(""), { dismissed++ }, { opened++ }) }
            settle()
            instrumentation.runOnMainSync {
                val modal = checkNotNull(dialog)
                assertActionsVisible(modal)
                assertNull(modal.findViewById<View>(R.id.native_update_notes))
                modal.findViewById<View>(R.id.native_update_cancel).performClick()
            }
            settle()
            assertEquals(0, opened)
            assertEquals(1, dismissed)
        }
    }

    @Test fun systemBackDismissesAnnouncementWithoutOpeningRelease() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            var dialog: Dialog? = null
            var opened = 0
            var dismissed = 0
            scenario.onActivity { dialog = it.pageUi.showUpdateDialog(release(longNotes), { dismissed++ }, { opened++ }) }
            settle()
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            settle()
            assertFalse(checkNotNull(dialog).isShowing)
            assertEquals(0, opened)
            assertEquals(1, dismissed)
        }
    }

    @Test fun largeFontLandscapeKeepsBothActionsInsideTheWindow() {
        val previous = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 1.5")
            ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
                SystemClock.sleep(650)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                SystemClock.sleep(1000)
                var dialog: Dialog? = null
                scenario.onActivity {
                    assertTrue(it.resources.configuration.fontScale >= 1.45f)
                    assertTrue(it.window.decorView.width > it.window.decorView.height)
                    dialog = it.pageUi.showUpdateDialog(release(longNotes), {}, {})
                }
                settle()
                instrumentation.runOnMainSync { assertActionsVisible(checkNotNull(dialog)) }
                capture("update-landscape-large-font.png")
                instrumentation.runOnMainSync { dialog?.dismiss() }
            }
        } finally {
            shell(if (previous == "null" || previous.isBlank()) "settings delete system font_scale" else "settings put system font_scale $previous")
        }
    }

    @Test fun truncatedAnnouncementExplainsWhereToReadTheRest() {
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            var dialog: Dialog? = null
            scenario.onActivity { dialog = it.pageUi.showUpdateDialog(release("x".repeat(40000)), {}, {}) }
            settle()
            instrumentation.runOnMainSync {
                val modal = checkNotNull(dialog)
                assertActionsVisible(modal)
                assertTrue(descendants(checkNotNull(modal.window).decorView).filterIsInstance<TextView>()
                    .any { it.text.toString() == instrumentation.targetContext.getString(R.string.update_notes_truncated) })
                modal.findViewById<View>(R.id.native_update_cancel).performClick()
            }
            settle()
        }
    }

    @Test fun oversizedTableKeepsAllContentWithABoundedNativeViewCount() {
        val rows = (1..300).joinToString("\n") { "| 文件$it | 更新内容$it |" }
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(650)
            var dialog: Dialog? = null
            scenario.onActivity { dialog = it.pageUi.showUpdateDialog(release("| 文件 | 说明 |\n| --- | --- |\n$rows"), {}, {}) }
            settle()
            instrumentation.runOnMainSync {
                val modal = checkNotNull(dialog)
                assertActionsVisible(modal)
                val texts = descendants(checkNotNull(modal.window).decorView).filterIsInstance<TextView>()
                assertTrue("Large table allocates excessive cell views", texts.size < 40)
                assertTrue(texts.any { it.text.contains("更新内容300") })
                modal.findViewById<View>(R.id.native_update_cancel).performClick()
            }
            settle()
        }
    }

    @Test fun advancedMaterialAnnouncementRetainsReadableContentAndActions() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags("zh-CN")
            assertTrue(LumenEngine.selectMaterial(context, SkinId.LIQUID, true))
        }
        ActivityScenario.launch(BasicActivity::class.java).use { scenario ->
            SystemClock.sleep(900)
            var dialog: Dialog? = null
            scenario.onActivity { dialog = it.pageUi.showUpdateDialog(release(longNotes), {}, {}) }
            settle()
            instrumentation.runOnMainSync { assertActionsVisible(checkNotNull(dialog)) }
            capture("update-announcement-liquid.png")
            val samplePosition = IntArray(2)
            var surfaceColor = 0
            scenario.onActivity { activity ->
                surfaceColor = activity.lumen.palette.surface
                val scroll = activity.findViewById<ScrollView>(R.id.native_scroll)
                scroll.removeAllViews()
                scroll.addView(View(activity).apply { setBackgroundColor(android.graphics.Color.MAGENTA) },
                    android.widget.FrameLayout.LayoutParams(-1, activity.dp(2000)))
                val card = checkNotNull(dialog).findViewById<ScrollView>(R.id.native_update_notes).parent as View
                card.getLocationOnScreen(samplePosition)
                samplePosition[0] += activity.dp(12)
                samplePosition[1] += card.height / 2
            }
            settle()
            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val sample = screenshot.getPixel(samplePosition[0], samplePosition[1])
                val difference = maxOf(
                    kotlin.math.abs(android.graphics.Color.red(sample) - android.graphics.Color.red(surfaceColor)),
                    kotlin.math.abs(android.graphics.Color.green(sample) - android.graphics.Color.green(surfaceColor)),
                    kotlin.math.abs(android.graphics.Color.blue(sample) - android.graphics.Color.blue(surfaceColor)))
                assertTrue("Underlying content overwhelms the announcement backing: $difference", difference <= 32)
            } finally { screenshot.recycle() }
            instrumentation.runOnMainSync { dialog?.findViewById<View>(R.id.native_update_cancel)?.performClick() }
            settle()
        }
    }

    private fun release(notes: String) = ReleaseInfo("v9.0.0", "9.0.0", notes,
        "https://github.com/jichuo1/SplashScreenAdvanced/releases/tag/v9.0.0", null, false)

    private fun assertActionsVisible(dialog: Dialog) {
        val decor = checkNotNull(dialog.window).decorView
        val bounds = Rect(); assertTrue(decor.getGlobalVisibleRect(bounds))
        listOf(R.id.native_update_action, R.id.native_update_cancel).forEach { id ->
            val action = dialog.findViewById<View>(id)
            val visible = Rect(); assertTrue("Action is hidden", action.getGlobalVisibleRect(visible))
            assertEquals("Action is clipped vertically", action.height, visible.height())
            assertEquals("Action is clipped horizontally", action.width, visible.width())
            assertTrue("Action outside the dialog window", bounds.contains(visible))
            assertTrue(action.isEnabled && action.isClickable)
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is android.view.ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(450) }
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun capture(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }

    private val longNotes = """
        <div align="center">
        ## 🧬 SplashScreenAdvanced 9.0.0
        _✨ 更新说明 · 稳定版_
        </div>
        ### 🛠️ 修复
        **修复**图标缓存与页面显示。
        <details>
        <summary>📜 完整提交记录</summary>
        - [查看提交](https://github.com/jichuo1/SplashScreenAdvanced/commit/abc123)
        </details>
        > 更新后请阅读说明。

        | 文件 | 说明 |
        | --- | --- |
        | `SplashScreenAdvanced.apk` | SHA-256 `1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234` |

    """.trimIndent() + "\n\n" + (1..120).joinToString("\n") { "- 第 $it 项：**更新内容**与兼容性修复，完整说明可滚动查看。" } + "\n\nEND_OF_ANNOUNCEMENT"
}
