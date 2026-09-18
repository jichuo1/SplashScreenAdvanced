package dev.lackluster.hyperx.ui.animation

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val ownerKey: Any,
    val id: String,
    val singleLine: Boolean = true,
)

private data class PageOrigin(val anchor: PageMotionAnchor, val cornerRadiusPx: Float)

/** One motion transaction at a time; each destination owns its own return origin. */
@Stable
class PageMotionController(
    private val scope: CoroutineScope,
    private val popBackStack: (Any) -> Unit,
    private val nowMillis: () -> Long = SystemClock::uptimeMillis,
    private val animateProgress: suspend (Int, (Float) -> Unit) -> Unit = { duration, update ->
        animate(0f, 1f, animationSpec = tween(duration, easing = LinearEasing)) { value, _ ->
            update(value)
        }
    },
) {
    internal val session = NavigationMotionSession()
    internal var phase by mutableStateOf(NavigationMotionPhase.EXPANDED)
        private set
    var expansion by mutableFloatStateOf(1f)
        private set
    internal var currentKey by mutableStateOf<Any?>(null)
        private set
    internal var source by mutableStateOf<PageMotionAnchor?>(null)
        private set
    internal var target by mutableStateOf<PageMotionAnchor?>(null)
        private set
    internal var expandedBounds by mutableStateOf(Rect.Zero)
        private set
    internal var contentTiming by mutableStateOf(PageContentTiming.TIMED)
        private set
    internal var collapsedCornerRadiusPx = 0f
    internal var contentTravelPx = 0f
    private val origins = mutableMapOf<Any, PageOrigin>()
    private val titles = mutableMapOf<Any, PageMotionAnchor>()
    private val backTargets = mutableMapOf<Any, Rect>()
    private var pendingCapture: PageOrigin? = null
    private var animationJob: Job? = null
    private var gestureStartExpansion = 1f

    val isActive: Boolean get() = phase != NavigationMotionPhase.EXPANDED
    val inputBlocked: Boolean get() = isActive
    val canPush: Boolean get() = !isActive
    internal val overlayVisible: Boolean
        get() = isActive && source?.singleLine == true && target?.singleLine == true

    fun isHidingSourceTitle(owner: Any?, id: String): Boolean =
        overlayVisible && source?.ownerKey == owner && source?.id == id

    fun isHidingTargetTitle(owner: Any?): Boolean = overlayVisible && currentKey == owner

    /** Capture is valid only for the synchronous click that actually pushes a route. */
    fun withSource(anchor: PageMotionAnchor?, cornerRadiusPx: Float, action: () -> Unit) {
        pendingCapture = anchor?.takeIf { it.rowBounds.toMotionRect().isValid }
            ?.let { PageOrigin(it, cornerRadiusPx) }
        try { action() } finally { pendingCapture = null }
    }

    fun reportSource(anchor: PageMotionAnchor) {
        // Never retarget geometry halfway through a gesture/animation.
        if (isActive) return
        origins.entries.forEach { entry ->
            if (entry.value.anchor.ownerKey == anchor.ownerKey && entry.value.anchor.id == anchor.id) {
                entry.setValue(entry.value.copy(anchor = anchor))
            }
        }
    }

    fun reportTarget(anchor: PageMotionAnchor) {
        val offset = if (anchor.ownerKey == currentKey && isActive) {
            contentTravelPx * (1f - PageEnterMotionSpec.contentFraction(expansion, contentTiming))
        } else 0f
        val measured = anchor.copy(bounds = anchor.bounds.translate(0f, -offset))
        if (measured.bounds.toMotionRect().isValid) titles[anchor.ownerKey] = measured
        if (anchor.ownerKey == currentKey && !isActive) target = measured
    }

    fun reportBackTarget(owner: Any, bounds: Rect) { backTargets[owner] = bounds }
    internal fun isBackTarget(x: Float, y: Float): Boolean =
        backTargets[currentKey]?.contains(androidx.compose.ui.geometry.Offset(x, y)) == true

    fun syncBackStack(keys: List<Any>) {
        origins.keys.retainAll(keys.toSet())
        titles.keys.retainAll(keys.toSet())
        backTargets.keys.retainAll(keys.toSet())
        if (currentKey != keys.lastOrNull()) {
            settle()
            currentKey = keys.lastOrNull()
            source = origins[currentKey]?.anchor
            target = titles[currentKey]
        }
    }

    fun onNavigateForward(key: Any): Boolean {
        if (!canPush) return false
        cancelAnimation()
        currentKey = key
        pendingCapture?.let { origins[key] = it }
        source = pendingCapture?.anchor
        collapsedCornerRadiusPx = pendingCapture?.cornerRadiusPx ?: 0f
        target = null
        contentTiming = PageContentTiming.TIMED
        expansion = 0f
        phase = NavigationMotionPhase.PREPARING_ENTRY
        session.reset(0f, nowMillis())
        return true
    }

    /** The host calls this after the new page has been measured, with a generation guard. */
    fun onDestinationPresented(key: Any, generation: Long) {
        if (currentKey != key || !session.owns(generation) ||
            phase != NavigationMotionPhase.PREPARING_ENTRY) return
        target = titles[key]
        phase = NavigationMotionPhase.ENTERING
        animateTo(1f, PageEnterMotionSpec.ENTER_DURATION_MS, enterEasing, false) { settle() }
    }

    fun onNavigateBack(key: Any): Boolean {
        if (currentKey != key) return false
        if (phase == NavigationMotionPhase.CLOSING) return true
        if (!isActive) prepareReturn()
        phase = NavigationMotionPhase.CLOSING
        animateTo(0f, PageEnterMotionSpec.CLOSE_DURATION_MS, closeEasing, true) { finishClose(key) }
        return true
    }

    fun beginPredictive(): Boolean {
        if (currentKey == null) return false
        if (!isActive) {
            prepareReturn()
            contentTiming = PageContentTiming.PREDICTIVE
        }
        cancelAnimation()
        gestureStartExpansion = expansion
        phase = NavigationMotionPhase.PREDICTIVE_BACK
        return true
    }

    fun seekPredictive(progress: Float) {
        if (phase == NavigationMotionPhase.PREDICTIVE_BACK) {
            applyExpansion(NavigationMotionPolicy.predictiveExpansion(gestureStartExpansion, progress))
        }
    }

    fun cancelPredictive() {
        if (phase != NavigationMotionPhase.PREDICTIVE_BACK) return
        phase = NavigationMotionPhase.CANCELLING_BACK
        // Keep the exact content/title profile of the interrupted frame until fully expanded.
        animateTo(1f, PageEnterMotionSpec.CANCEL_DURATION_MS, enterEasing, true) { settle() }
    }

    fun commitPredictive() {
        if (phase != NavigationMotionPhase.PREDICTIVE_BACK) return
        val key = currentKey ?: return
        phase = NavigationMotionPhase.CLOSING
        animateTo(0f, PageEnterMotionSpec.COMMIT_DURATION_MS, commitEasing, true) { finishClose(key) }
    }

    private fun prepareReturn() {
        val origin = origins[currentKey]
        source = origin?.anchor
        collapsedCornerRadiusPx = origin?.cornerRadiusPx ?: 0f
        target = titles[currentKey]
        contentTiming = PageContentTiming.TIMED
    }

    fun updateHostBounds(bounds: Rect) {
        if (expandedBounds == bounds) return
        val hadBounds = expandedBounds.toMotionRect().isValid
        expandedBounds = bounds
        if (hadBounds && isActive) {
            val closingKey = currentKey.takeIf { phase == NavigationMotionPhase.CLOSING }
            settle()
            if (closingKey != null) finishClose(closingKey)
        }
    }

    fun resetAfterImmediatePop() { settle() }

    fun onHostDisposed() {
        settle()
        origins.clear()
        titles.clear()
        backTargets.clear()
        source = null
        target = null
        expandedBounds = Rect.Zero
    }

    private fun finishClose(key: Any) {
        if (currentKey != key) return
        origins.remove(key)
        settle()
        popBackStack(key)
    }

    private fun settle() {
        cancelAnimation()
        expansion = 1f
        phase = NavigationMotionPhase.EXPANDED
        contentTiming = PageContentTiming.TIMED
        session.reset(1f, nowMillis())
    }

    private fun animateTo(
        end: Float,
        duration: Long,
        easing: CubicBezierEasing,
        continueVelocity: Boolean,
        onEnd: () -> Unit,
    ) {
        val start = expansion
        val velocity = session.velocity(nowMillis())
        cancelAnimation()
        if (kotlin.math.abs(start - end) < 0.001f) {
            applyExpansion(end)
            onEnd()
            return
        }
        val actualDuration = NavigationMotionPolicy.remainingDuration(duration, start, end)
        val continuation = if (continueVelocity && kotlin.math.abs(velocity) > 0.01f) {
            NavigationMotionContinuation(start, end, velocity, actualDuration)
        } else null
        val token = session.generation
        session.reset(start, nowMillis())
        animationJob = scope.launch {
            animateProgress(actualDuration.toInt()) { fraction ->
                if (session.owns(token)) {
                    applyExpansion(continuation?.value(fraction) ?: (start + (end - start) * easing.transform(fraction)))
                }
            }
            if (session.owns(token)) onEnd()
        }
    }

    private fun applyExpansion(value: Float) {
        expansion = value.coerceIn(0f, 1f)
        session.sample(expansion, nowMillis())
    }

    private fun cancelAnimation() {
        session.invalidate()
        animationJob?.cancel()
        animationJob = null
    }

    internal fun fillFrame(buffer: PageMotionFrameBuffer) {
        val expanded = expandedBounds.toMotionRect()
        val collapsed = source?.rowBounds?.toMotionRect()?.takeIf { it.isValid }
            ?: MotionRect(
                expanded.left + expanded.width * 0.04f, expanded.top + expanded.height * 0.06f,
                expanded.right - expanded.width * 0.04f, expanded.bottom - expanded.height * 0.06f,
            )
        PageEnterMotionSpec.fillFrame(
            buffer, expansion, collapsed, expanded,
            source?.bounds?.toMotionRect() ?: collapsed,
            target?.bounds?.toMotionRect() ?: expanded,
            source?.fontSizePx ?: 16f, target?.fontSizePx ?: 16f,
            collapsedCornerRadiusPx, contentTravelPx, contentTiming,
        )
    }

    companion object {
        internal val enterEasing = HyperXNavTransitions.EnterEasing
        internal val closeEasing = HyperXNavTransitions.CloseEasing
        internal val commitEasing = HyperXNavTransitions.CommitEasing
    }
}

internal fun Rect.toMotionRect(): MotionRect = MotionRect(left, top, right, bottom)
