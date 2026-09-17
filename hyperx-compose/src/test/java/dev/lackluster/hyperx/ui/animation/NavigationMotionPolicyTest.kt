package dev.lackluster.hyperx.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NavigationMotionPolicyTest {
    @Test
    fun aBackPressKeepsTouchOwnershipAcrossAnimationCompletionUntilUpOrCancel() {
        assertTrue(NavigationMotionPolicy.keepInputBlocked(true, false))
        assertTrue(NavigationMotionPolicy.keepInputBlocked(true, true))
        assertTrue(NavigationMotionPolicy.keepInputBlocked(false, true))
        assertFalse(NavigationMotionPolicy.keepInputBlocked(false, false))
    }

    @Test
    fun entryExpandedAndCancelReboundAllowNavigationButNeverBusinessWork() {
        for (phase in NavigationMotionPhase.entries) {
            assertFalse(NavigationMotionPolicy.canNavigate(phase, true))
            assertEquals(
                phase == NavigationMotionPhase.ENTERING ||
                    phase == NavigationMotionPhase.EXPANDED ||
                    phase == NavigationMotionPhase.CANCELLING_BACK,
                NavigationMotionPolicy.canNavigate(phase, false)
            )
        }
    }

    @Test
    fun onlyUnsettledReusableFramesPreserveTheirOriginalProfile() {
        for (phase in NavigationMotionPhase.entries) {
            assertEquals(
                phase in listOf(
                    NavigationMotionPhase.ENTERING,
                    NavigationMotionPhase.PREDICTIVE_BACK,
                    NavigationMotionPhase.CANCELLING_BACK
                ),
                NavigationMotionPolicy.preserveFrame(phase)
            )
        }
    }

    @Test
    fun interruptedMotionStartsAtTheExactCurrentValueAndEndsAtTheRequestedEndpoint() {
        for (start in listOf(0f, 0.01f, 0.3f, 0.8f, 0.99f, 1f)) {
            for (target in listOf(0f, 1f)) {
                for (velocity in listOf(-8f, -1f, 0f, 1f, 8f)) {
                    val curve = NavigationMotionContinuation(start, target, velocity, 320L)
                    assertEquals(start, curve.value(0f), 0f)
                    assertEquals(target, curve.value(1f), 0.000001f)
                    repeat(1001) { assertTrue(curve.value(it / 1000f) in 0f..1f) }
                }
            }
        }
    }

    @Test
    fun moderateReversalContinuesVelocityBeforeTurningWithoutAPositionJump() {
        val curve = NavigationMotionContinuation(0.5f, 0f, 0.5f, 300L)
        val initialVelocity = (curve.value(0.0001f) - 0.5f) / 0.00003f
        assertEquals(0.5f, initialVelocity, 0.015f)
        assertTrue(curve.value(0.001f) > 0.5f)
        assertTrue(curve.value(0.8f) < 0.5f)
    }

    @Test
    fun reboundDurationUsesRemainingDistanceWithABoundedMinimum() {
        assertEquals(210L, NavigationMotionPolicy.remainingDuration(210L, 0f, 1f))
        assertEquals(105L, NavigationMotionPolicy.remainingDuration(210L, 0.5f, 1f))
        assertEquals(80L, NavigationMotionPolicy.remainingDuration(210L, 0.99f, 1f))
    }

    @Test
    fun velocityIsBoundedAndStaleSamplesAreNotReused() {
        val session = NavigationMotionSession()
        session.reset(0.2f, 1000L)
        session.sample(0.3f, 1050L)
        assertEquals(2f, session.velocity(1050L), 0.00001f)
        assertEquals(0f, session.velocity(1200L), 0f)
        session.sample(1f, 1051L)
        assertEquals(8f, session.velocity(1051L), 0f)
        session.reset(0.6f, 2000L)
        assertEquals(0f, session.velocity(2000L), 0f)
    }

    @Test
    fun titleMotionHandoffDoesNotDoubleDrawNativeText() {
        for (progress in listOf(0f, 0.12f, 0.5f, 0.85f, 1f)) {
            assertEquals(0f, TitleMotionSpec.sourceWeight(progress), 0f)
            assertEquals(0f, TitleMotionSpec.targetWeight(progress), 0f)
            assertEquals(1f, TitleMotionSpec.overlayWeight(progress), 0f)
        }
        assertEquals(0f, TitleMotionSpec.motionProgress(0.12f), 0f)
        assertEquals(1f, TitleMotionSpec.motionProgress(0.85f), 0f)
        assertTrue(TitleMotionSpec.titleLineMatches("作用域", "作用域"))
        assertTrue(TitleMotionSpec.titleLineMatches("作用域\n摘要", "作用域"))
        assertFalse(TitleMotionSpec.titleLineMatches("显示设置", "显示"))
    }

    @Test
    fun contentStaysHiddenUntilTitleHasMostlyArrived() {
        assertEquals(0f, PageEnterMotionSpec.contentFraction(0.8f, PageContentTiming.TIMED), 0f)
        assertTrue(PageEnterMotionSpec.contentFraction(0.95f, PageContentTiming.TIMED) > 0.5f)
        assertTrue(PageEnterMotionSpec.contentFraction(0.8f, PageContentTiming.PREDICTIVE) > 0.9f)
        val buffer = PageMotionFrameBuffer()
        PageEnterMotionSpec.fillFrame(
            out = buffer,
            expansion = 0.5f,
            collapsedBounds = MotionRect(10f, 20f, 110f, 80f),
            expandedBounds = MotionRect(0f, 0f, 400f, 800f),
            collapsedTitleBounds = MotionRect(20f, 30f, 90f, 50f),
            expandedTitleBounds = MotionRect(40f, 80f, 200f, 120f),
            collapsedTitleTextSizePx = 16f,
            expandedTitleTextSizePx = 32f,
            collapsedCornerRadiusPx = 24f,
            contentTravelPx = 12f,
        )
        assertEquals(5f, buffer.left, 0.01f)
        assertEquals(12f, buffer.cornerRadiusPx, 0.01f)
        assertTrue(abs(buffer.titleX - 30f) < 0.01f)
    }
}
