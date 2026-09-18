package dev.lackluster.hyperx.ui.animation

import kotlin.math.roundToLong

internal data class MotionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isValid: Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            width > 0f && height > 0f
}

internal enum class PageContentTiming {
    TIMED,
    PREDICTIVE,
}

internal class PageMotionFrameBuffer {
    var left = 0f
        private set
    var top = 0f
        private set
    var right = 0f
        private set
    var bottom = 0f
        private set
    var cornerRadiusPx = 0f
        private set
    var surfaceAlpha = 0f
        private set
    var contentAlpha = 0f
        private set
    var contentTranslationYPx = 0f
        private set
    var titleX = 0f
        private set
    var titleY = 0f
        private set
    var titleTextSizePx = 1f
        private set

    fun set(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cornerRadiusPx: Float,
        surfaceAlpha: Float,
        contentAlpha: Float,
        contentTranslationYPx: Float,
        titleX: Float,
        titleY: Float,
        titleTextSizePx: Float,
    ) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.cornerRadiusPx = cornerRadiusPx
        this.surfaceAlpha = surfaceAlpha
        this.contentAlpha = contentAlpha
        this.contentTranslationYPx = contentTranslationYPx
        this.titleX = titleX
        this.titleY = titleY
        this.titleTextSizePx = titleTextSizePx
    }
}

internal object PageEnterMotionSpec {
    const val ENTER_EASING_X1 = 0.05f
    const val ENTER_EASING_Y1 = 0.7f
    const val ENTER_EASING_X2 = 0.1f
    const val ENTER_EASING_Y2 = 1f

    const val CLOSE_EASING_X1 = 0.4f
    const val CLOSE_EASING_Y1 = 0f
    const val CLOSE_EASING_X2 = 0.2f
    const val CLOSE_EASING_Y2 = 1f

    const val COMMIT_EASING_X1 = 0f
    const val COMMIT_EASING_Y1 = 0f
    const val COMMIT_EASING_X2 = 0.2f
    const val COMMIT_EASING_Y2 = 1f

    const val ENTER_DURATION_MS = 370L
    const val CLOSE_DURATION_MS = 320L
    const val CANCEL_DURATION_MS = 260L
    const val COMMIT_DURATION_MS = 200L
    const val MIN_CLOSE_DURATION_MS = 80L
    const val CONTENT_TRAVEL_DP = 12f
    const val SURFACE_HANDOFF = 0.1f
    const val CAPTURE_TTL_MS = 500L

    fun closeDurationMs(baseDurationMs: Long, currentExpansion: Float, minimumDurationMs: Long): Long {
        val minimum = minimumDurationMs.coerceIn(0L, baseDurationMs)
        return (baseDurationMs * currentExpansion.coerceIn(0f, 1f))
            .roundToLong()
            .coerceIn(minimum, baseDurationMs)
    }

    fun fillFrame(
        out: PageMotionFrameBuffer,
        expansion: Float,
        collapsedBounds: MotionRect,
        expandedBounds: MotionRect,
        collapsedTitleBounds: MotionRect,
        expandedTitleBounds: MotionRect,
        collapsedTitleTextSizePx: Float,
        expandedTitleTextSizePx: Float,
        collapsedCornerRadiusPx: Float,
        contentTravelPx: Float,
        contentTiming: PageContentTiming = PageContentTiming.TIMED,
    ) {
        val fraction = expansion.coerceIn(0f, 1f)
        val contentFraction = contentFraction(fraction, contentTiming)
        out.set(
            left = lerp(collapsedBounds.left, expandedBounds.left, fraction),
            top = lerp(collapsedBounds.top, expandedBounds.top, fraction),
            right = lerp(collapsedBounds.right, expandedBounds.right, fraction),
            bottom = lerp(collapsedBounds.bottom, expandedBounds.bottom, fraction),
            cornerRadiusPx = lerp(collapsedCornerRadiusPx.coerceAtLeast(0f), 0f, fraction),
            surfaceAlpha = smoothStep(0f, SURFACE_HANDOFF, fraction),
            contentAlpha = contentFraction,
            contentTranslationYPx = lerp(contentTravelPx.coerceAtLeast(0f), 0f, contentFraction),
            titleX = lerp(collapsedTitleBounds.left, expandedTitleBounds.left, fraction),
            titleY = lerp(collapsedTitleBounds.top, expandedTitleBounds.top, fraction),
            titleTextSizePx = lerp(
                collapsedTitleTextSizePx.coerceAtLeast(1f),
                expandedTitleTextSizePx.coerceAtLeast(1f),
                fraction
            ),
        )
    }

    fun contentFraction(expansion: Float, timing: PageContentTiming): Float = when (timing) {
        PageContentTiming.TIMED -> smoothStep(0.86f, 0.985f, expansion)
        PageContentTiming.PREDICTIVE -> smoothStep(0.22f, 0.72f, expansion)
    }

    fun collapsedChromeFraction(expansion: Float): Float =
        1f - smoothStep(0f, 0.28f, expansion)

    fun transitionSurfaceAlpha(expansion: Float, handoffExpansion: Float): Float = smoothStep(
        0f,
        handoffExpansion.coerceIn(0.001f, 1f),
        expansion
    )

    fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
        if (edgeStart >= edgeEnd) return if (value < edgeStart) 0f else 1f
        val fraction = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction
}
