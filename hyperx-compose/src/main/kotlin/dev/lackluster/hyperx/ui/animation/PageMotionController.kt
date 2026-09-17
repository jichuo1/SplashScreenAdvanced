package dev.lackluster.hyperx.ui.animation

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PageMotionAnchor(
    val text: String,
    val bounds: Rect,
    val rowBounds: Rect,
    val fontSizePx: Float,
    val color: Color,
    val fontWeight: FontWeight,
)

internal enum class PageTitleMode {
    SOURCE_TITLE,
    HIDDEN,
}

@Stable
class PageMotionController(
    private val scope: CoroutineScope,
    private val popBackStack: () -> Unit,
) {
    internal val session = NavigationMotionSession()
    internal var phase by mutableStateOf(NavigationMotionPhase.EXPANDED)
        private set
    var expansion by mutableFloatStateOf(1f)
        private set
    internal var source by mutableStateOf<PageMotionAnchor?>(null)
        private set
    internal var target by mutableStateOf<PageMotionAnchor?>(null)
        private set
    internal var expandedBounds by mutableStateOf(Rect.Zero)
    internal var titleMode by mutableStateOf(PageTitleMode.HIDDEN)
        private set
    internal var contentTiming by mutableStateOf(PageContentTiming.TIMED)
        private set
    internal var collapsedCornerRadiusPx by mutableFloatStateOf(0f)
    internal var contentTravelPx by mutableFloatStateOf(0f)

    private var pendingCapture: PageMotionAnchor? = null
    private var captureAt = 0L
    private var animationJob: Job? = null
    private var popWhenClosed = false
    private var backPressActive = false
    private val origins = mutableMapOf<Any, PageMotionAnchor>()
    private var currentKey: Any? = null

    val isActive: Boolean
        get() = phase != NavigationMotionPhase.EXPANDED && phase != NavigationMotionPhase.FINISHED

    val inputBlocked: Boolean
        get() = NavigationMotionPolicy.keepInputBlocked(
            animationBlocked = isActive && phase != NavigationMotionPhase.EXPANDED,
            backPressActive = backPressActive
        )

    val overlayVisible: Boolean
        get() = titleMode == PageTitleMode.SOURCE_TITLE &&
            source != null &&
            target != null &&
            phase != NavigationMotionPhase.EXPANDED &&
            phase != NavigationMotionPhase.FINISHED

    fun isHidingSourceTitle(title: String): Boolean =
        overlayVisible && TitleMotionSpec.matches(source?.text.orEmpty(), title)

    fun isHidingTargetTitle(title: String): Boolean {
        if (titleMode != PageTitleMode.SOURCE_TITLE) return false
        return TitleMotionSpec.matches(target?.text.orEmpty(), title) ||
            TitleMotionSpec.matches(source?.text.orEmpty(), title)
    }

    fun captureSource(anchor: PageMotionAnchor, cornerRadiusPx: Float) {
        pendingCapture = anchor
        captureAt = SystemClock.uptimeMillis()
        collapsedCornerRadiusPx = cornerRadiusPx
    }

    fun reportTarget(anchor: PageMotionAnchor) {
        if (NavigationMotionPolicy.preserveFrame(phase) && target != null) return
        target = anchor
        if (titleMode == PageTitleMode.HIDDEN && canMoveTitle()) {
            titleMode = PageTitleMode.SOURCE_TITLE
        }
    }

    fun onNavigateForward(key: Any) {
        session.invalidate()
        cancelAnimation()
        val now = SystemClock.uptimeMillis()
        val captured = pendingCapture.takeIf { now - captureAt <= PageEnterMotionSpec.CAPTURE_TTL_MS }
        pendingCapture = null
        currentKey = key
        if (captured != null) origins[key] = captured
        source = captured
        target = null
        titleMode = PageTitleMode.HIDDEN
        contentTiming = PageContentTiming.TIMED
        phase = NavigationMotionPhase.PREPARING_ENTRY
        expansion = 0f
        popWhenClosed = false
        session.reset(0f, now)
    }

    fun onDestinationPresented() {
        if (phase != NavigationMotionPhase.PREPARING_ENTRY) return
        titleMode = if (canMoveTitle()) PageTitleMode.SOURCE_TITLE else PageTitleMode.HIDDEN
        phase = NavigationMotionPhase.ENTERING
        animateExpansion(
            targetExpansion = 1f,
            durationMs = PageEnterMotionSpec.ENTER_DURATION_MS,
            easing = enterEasing,
            retarget = false,
            onEnd = { completeExpanded() },
        )
    }

    fun onNavigateBack(key: Any): Boolean {
        if (phase == NavigationMotionPhase.CLOSING) return true
        if (phase == NavigationMotionPhase.PREPARING_ENTRY || phase == NavigationMotionPhase.FINISHED) {
            origins.remove(key)
            resetIdle()
            return false
        }
        if (!NavigationMotionPolicy.canNavigate(phase, businessBlocked = false) &&
            phase != NavigationMotionPhase.PREDICTIVE_BACK
        ) {
            return false
        }
        currentKey = key
        origins[key]?.let { source = it }
        if (source == null) {
            origins.remove(key)
            resetIdle()
            return false
        }
        requestClose(interactiveCommit = false)
        return true
    }

    fun beginPredictive(): Boolean {
        if (source == null) return false
        if (phase != NavigationMotionPhase.ENTERING &&
            phase != NavigationMotionPhase.EXPANDED &&
            phase != NavigationMotionPhase.CANCELLING_BACK
        ) return false
        cancelAnimation()
        if (!NavigationMotionPolicy.preserveFrame(phase)) {
            contentTiming = PageContentTiming.PREDICTIVE
        }
        phase = NavigationMotionPhase.PREDICTIVE_BACK
        backPressActive = true
        return true
    }

    fun seekPredictive(backProgress: Float) {
        if (phase != NavigationMotionPhase.PREDICTIVE_BACK) return
        val value = (1f - backProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        applyExpansion(value)
    }

    fun cancelPredictive() {
        if (phase != NavigationMotionPhase.PREDICTIVE_BACK) return
        backPressActive = false
        phase = NavigationMotionPhase.CANCELLING_BACK
        contentTiming = PageContentTiming.TIMED
        animateExpansion(
            targetExpansion = 1f,
            durationMs = PageEnterMotionSpec.CANCEL_DURATION_MS,
            easing = enterEasing,
            retarget = true,
            onEnd = { completeExpanded() },
        )
    }

    fun commitPredictive() {
        if (phase != NavigationMotionPhase.PREDICTIVE_BACK) return
        backPressActive = false
        requestClose(interactiveCommit = true)
    }

    private fun requestClose(interactiveCommit: Boolean) {
        val retarget = NavigationMotionPolicy.preserveFrame(phase) && !interactiveCommit
        cancelAnimation()
        if (!interactiveCommit && !retarget) {
            contentTiming = PageContentTiming.TIMED
        }
        if (canMoveTitle()) titleMode = PageTitleMode.SOURCE_TITLE
        phase = NavigationMotionPhase.CLOSING
        popWhenClosed = true
        val current = expansion
        if (current <= 0.001f) {
            finishClose()
            return
        }
        val base = if (interactiveCommit) {
            PageEnterMotionSpec.COMMIT_DURATION_MS
        } else {
            PageEnterMotionSpec.CLOSE_DURATION_MS
        }
        val duration = PageEnterMotionSpec.closeDurationMs(
            baseDurationMs = base,
            currentExpansion = current,
            minimumDurationMs = PageEnterMotionSpec.MIN_CLOSE_DURATION_MS
        )
        animateExpansion(
            targetExpansion = 0f,
            durationMs = duration,
            easing = if (interactiveCommit) commitEasing else closeEasing,
            retarget = retarget,
            onEnd = { finishClose() },
        )
    }

    private fun completeExpanded() {
        phase = NavigationMotionPhase.EXPANDED
        expansion = 1f
        titleMode = PageTitleMode.HIDDEN
        contentTiming = PageContentTiming.TIMED
        popWhenClosed = false
        session.reset(1f, SystemClock.uptimeMillis())
    }

    private fun finishClose() {
        val shouldPop = popWhenClosed
        currentKey?.let(origins::remove)
        if (shouldPop) popBackStack()
        resetIdle()
    }

    private fun resetIdle() {
        cancelAnimation()
        phase = NavigationMotionPhase.EXPANDED
        expansion = 1f
        titleMode = PageTitleMode.HIDDEN
        contentTiming = PageContentTiming.TIMED
        popWhenClosed = false
        backPressActive = false
        source = null
        target = null
        pendingCapture = null
        currentKey = null
        session.reset(1f, SystemClock.uptimeMillis())
    }

    fun resetAfterImmediatePop() {
        resetIdle()
    }

    private fun canMoveTitle(): Boolean {
        val from = source ?: return false
        val to = target ?: return false
        return TitleMotionSpec.titleLineMatches(from.text, to.text)
    }

    private fun animateExpansion(
        targetExpansion: Float,
        durationMs: Long,
        easing: CubicBezierEasing,
        retarget: Boolean,
        onEnd: () -> Unit,
    ) {
        cancelAnimation()
        val start = expansion
        if (durationMs <= 0L || abs(start - targetExpansion) <= 0.001f) {
            applyExpansion(targetExpansion)
            onEnd()
            return
        }
        val actualDuration = if (retarget && targetExpansion == 1f) {
            NavigationMotionPolicy.remainingDuration(durationMs, start, targetExpansion)
        } else {
            durationMs
        }
        val now = SystemClock.uptimeMillis()
        val continuation = if (retarget) {
            NavigationMotionContinuation(start, targetExpansion, session.velocity(now), actualDuration)
        } else {
            null
        }
        session.reset(start, now)
        val token = session.generation
        animationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = actualDuration.toInt().coerceAtLeast(1),
                    easing = LinearEasing
                )
            ) { fraction, _ ->
                if (!session.owns(token)) return@animate
                val value = if (continuation != null) {
                    continuation.value(fraction)
                } else {
                    start + (targetExpansion - start) * easing.transform(fraction)
                }
                applyExpansion(value)
            }
            if (session.owns(token)) onEnd()
        }
    }

    private fun applyExpansion(value: Float) {
        expansion = value.coerceIn(0f, 1f)
        session.sample(expansion, SystemClock.uptimeMillis())
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
        session.invalidate()
    }

    internal val frame by derivedStateOf {
        val buffer = PageMotionFrameBuffer()
        val collapsed = source?.rowBounds?.toMotionRect()
        val expanded = expandedBounds.toMotionRect()
        if (collapsed == null || !collapsed.isValid || !expanded.isValid) {
            return@derivedStateOf null
        }
        val collapsedTitle = source?.bounds?.toMotionRect() ?: collapsed
        val expandedTitle = target?.bounds?.toMotionRect() ?: expanded
        PageEnterMotionSpec.fillFrame(
            out = buffer,
            expansion = expansion,
            collapsedBounds = collapsed,
            expandedBounds = expanded,
            collapsedTitleBounds = if (collapsedTitle.isValid) collapsedTitle else collapsed,
            expandedTitleBounds = if (expandedTitle.isValid) expandedTitle else expanded,
            collapsedTitleTextSizePx = source?.fontSizePx ?: 16f,
            expandedTitleTextSizePx = target?.fontSizePx ?: source?.fontSizePx ?: 16f,
            collapsedCornerRadiusPx = collapsedCornerRadiusPx,
            contentTravelPx = contentTravelPx,
            contentTiming = contentTiming,
        )
        buffer
    }

    companion object {
        internal val enterEasing = CubicBezierEasing(
            PageEnterMotionSpec.ENTER_EASING_X1,
            PageEnterMotionSpec.ENTER_EASING_Y1,
            PageEnterMotionSpec.ENTER_EASING_X2,
            PageEnterMotionSpec.ENTER_EASING_Y2,
        )
        internal val closeEasing = CubicBezierEasing(
            PageEnterMotionSpec.CLOSE_EASING_X1,
            PageEnterMotionSpec.CLOSE_EASING_Y1,
            PageEnterMotionSpec.CLOSE_EASING_X2,
            PageEnterMotionSpec.CLOSE_EASING_Y2,
        )
        internal val commitEasing = CubicBezierEasing(
            PageEnterMotionSpec.COMMIT_EASING_X1,
            PageEnterMotionSpec.COMMIT_EASING_Y1,
            PageEnterMotionSpec.COMMIT_EASING_X2,
            PageEnterMotionSpec.COMMIT_EASING_Y2,
        )
    }
}

internal fun Rect.toMotionRect(): MotionRect = MotionRect(left, top, right, bottom)
