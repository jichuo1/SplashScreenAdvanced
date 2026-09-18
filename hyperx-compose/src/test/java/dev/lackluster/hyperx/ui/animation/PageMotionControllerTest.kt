package dev.lackluster.hyperx.ui.animation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.resume

class PageMotionControllerTest {
    private var now = 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val runs = mutableListOf<Run>()
    private val popped = mutableListOf<Any>()
    private val motion = PageMotionController(scope, { popped += it }, { now }) { _, update ->
        suspendCancellableCoroutine { runs += Run(update, it) }
    }

    @After fun close() { scope.cancel() }

    private class Run(val update: (Float) -> Unit, val continuation: CancellableContinuation<Unit>)
    private fun advance(progress: Float) {
        now += 16
        val run = runs.last()
        run.update(progress)
        if (progress == 1f && run.continuation.isActive) run.continuation.resume(Unit)
    }

    private fun anchor(owner: String, name: String, top: Float = 200f) = PageMotionAnchor(
        name, Rect(32f, top + 12f, 150f, top + 36f), Rect(12f, top, 388f, top + 64f),
        18f, Color.Black, FontWeight.Medium, owner, "$owner-$name",
    )

    private fun open(key: String, from: PageMotionAnchor? = anchor("root", key)) {
        motion.updateHostBounds(Rect(0f, 0f, 400f, 800f))
        motion.withSource(from, 24f) { assertTrue(motion.onNavigateForward(key)) }
        motion.reportTarget(anchor(key, key, 56f))
        motion.onDestinationPresented(key, motion.session.generation)
    }

    private fun frame() = PageMotionFrameBuffer().also(motion::fillFrame)

    @Test fun entryBeginsAtMeasuredRowAndFinishesAtFullPage() {
        open("A")
        assertEquals(12f, frame().left, 0f)
        assertEquals(200f, frame().top, 0f)
        assertTrue(motion.isHidingSourceTitle("root", "root-A"))
        assertFalse(motion.isHidingSourceTitle("other-page", "root-A"))
        advance(1f)
        assertEquals(0f, frame().left, 0f)
        assertEquals(800f, frame().bottom, 0f)
        assertFalse(motion.isActive)
        assertFalse(motion.isHidingTargetTitle("A"))
    }

    @Test fun closingInterruptsEntryWithoutAJumpAndPopsExactlyOnce() {
        open("A")
        advance(0.15f)
        val before = frame()
        assertTrue(motion.onNavigateBack("A"))
        val after = frame()
        assertEquals(before.top, after.top, 0f)
        assertEquals(before.contentAlpha, after.contentAlpha, 0f)
        val count = runs.size
        assertTrue(motion.onNavigateBack("A"))
        assertEquals(count, runs.size)
        advance(1f)
        assertEquals(listOf("A"), popped)
    }

    @Test fun predictiveTakeoverStartsAtInterruptedExpansion() {
        open("A")
        advance(0.12f)
        val start = motion.expansion
        assertTrue(start < 1f)
        motion.beginPredictive()
        motion.seekPredictive(0f)
        assertEquals(start, motion.expansion, 0f)
        motion.seekPredictive(0.4f)
        assertEquals(start * 0.6f, motion.expansion, 0.00001f)
    }

    @Test fun cancelAndReversalPreserveTheContentProfileOfTheGesture() {
        open("A")
        advance(1f)
        motion.beginPredictive()
        motion.seekPredictive(0.5f)
        val before = frame()
        motion.cancelPredictive()
        assertEquals(PageContentTiming.PREDICTIVE, motion.contentTiming)
        assertEquals(before.contentAlpha, frame().contentAlpha, 0f)
        advance(0.2f)
        val rebound = frame()
        motion.onNavigateBack("A")
        assertEquals(rebound.top, frame().top, 0f)
        assertEquals(rebound.contentAlpha, frame().contentAlpha, 0f)
        advance(1f)
        assertEquals(listOf("A"), popped)
    }

    @Test fun eachNestedPageKeepsItsOwnOriginAfterTheChildCloses() {
        open("A", anchor("root", "A", 200f))
        advance(1f)
        open("B", anchor("A", "B", 500f))
        advance(1f)
        motion.onNavigateBack("B")
        advance(1f)
        motion.syncBackStack(listOf("root", "A"))
        motion.beginPredictive()
        motion.seekPredictive(1f)
        assertEquals(200f, frame().top, 0f)
        motion.commitPredictive()
        assertEquals(listOf("B", "A"), popped)
    }

    @Test fun restoredPageWithoutAnOriginCanStillPredictivelyClose() {
        motion.updateHostBounds(Rect(0f, 0f, 400f, 800f))
        motion.syncBackStack(listOf("root", "restored"))
        assertTrue(motion.beginPredictive())
        motion.seekPredictive(0.6f)
        assertTrue(frame().right > frame().left)
        motion.commitPredictive()
        advance(1f)
        assertEquals(listOf("restored"), popped)
    }

    @Test fun staleAnimationCallbacksCannotMoveOrPopTheNewDestination() {
        open("A")
        val oldRun = runs.last()
        motion.resetAfterImmediatePop()
        motion.syncBackStack(listOf("root"))
        open("B")
        oldRun.update(1f)
        assertEquals(0f, motion.expansion, 0f)
        assertTrue(popped.isEmpty())
        advance(1f)
        assertEquals("B", motion.currentKey)
    }

    @Test fun aNonNavigatingClickNeverLeaksItsSourceIntoTheNextPush() {
        motion.withSource(anchor("root", "dialog"), 24f) { /* dialog only */ }
        open("A", null)
        assertNull(motion.source)
        assertFalse(motion.overlayVisible)
        advance(1f)
        motion.onNavigateBack("A")
        advance(1f)
        assertEquals(listOf("A"), popped)
    }

    @Test fun repeatedForwardClicksAreRejectedWhileThePageIsMoving() {
        open("A")
        assertFalse(motion.onNavigateForward("B"))
        assertEquals("A", motion.currentKey)
        advance(1f)
        assertTrue(motion.canPush)
    }

    @Test fun resizeDuringCloseSettlesAndInvalidatesTheOldCompletion() {
        open("A")
        advance(1f)
        motion.onNavigateBack("A")
        val oldRun = runs.last()
        motion.updateHostBounds(Rect(0f, 0f, 800f, 400f))
        oldRun.update(1f)
        assertEquals(listOf("A"), popped)
        assertFalse(motion.isActive)
    }

    @Test fun outdatedFirstFrameCallbacksCannotRestartAnInterruptedEntry() {
        motion.withSource(anchor("root", "A"), 24f) { motion.onNavigateForward("A") }
        val token = motion.session.generation
        motion.onNavigateBack("A")
        motion.onDestinationPresented("A", token)
        assertFalse(motion.isActive)
        assertEquals(listOf("A"), popped)
    }

    @Test fun differentSourceAndDestinationLabelsStillShareTheTitleMotion() {
        open("设置详情", anchor("root", "打开设置"))
        assertTrue(motion.overlayVisible)
        assertEquals("打开设置", motion.source?.text)
        assertEquals("设置详情", motion.target?.text)
        advance(1f)
        assertFalse(motion.overlayVisible)
    }

    @Test fun multilineTitleFallsBackWithoutHidingEitherNativeLabel() {
        open("A", anchor("root", "A").copy(singleLine = false))
        assertFalse(motion.overlayVisible)
        assertFalse(motion.isHidingSourceTitle("root", "root-A"))
        assertFalse(motion.isHidingTargetTitle("A"))
        advance(1f)
        motion.onNavigateBack("A")
        advance(1f)
        assertEquals(listOf("A"), popped)
    }

    @Test fun measuredTitleRemovesTheTemporaryContentTranslation() {
        motion.contentTravelPx = 12f
        motion.updateHostBounds(Rect(0f, 0f, 400f, 800f))
        motion.withSource(anchor("root", "A"), 24f) { motion.onNavigateForward("A") }
        val measured = anchor("A", "A", 56f)
        motion.reportTarget(measured.copy(bounds = measured.bounds.translate(0f, 12f)))
        motion.onDestinationPresented("A", motion.session.generation)
        assertEquals(measured.bounds, motion.target?.bounds)
    }

    @Test fun layoutModeChangeDropsOriginsInTheOldCoordinateSpace() {
        open("A")
        advance(1f)
        motion.onHostDisposed()
        motion.updateHostBounds(Rect(400f, 0f, 800f, 800f))
        motion.beginPredictive()
        assertNull(motion.source)
        motion.commitPredictive()
        advance(1f)
        assertEquals(listOf("A"), popped)
    }
}
