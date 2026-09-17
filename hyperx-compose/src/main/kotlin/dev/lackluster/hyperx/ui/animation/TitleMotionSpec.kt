package dev.lackluster.hyperx.ui.animation

internal object TitleMotionSpec {
    fun matches(source: String, target: String): Boolean =
        source.isNotBlank() && source == target

    fun titleLineMatches(source: String, target: String): Boolean =
        matches(source, target) || (target.isNotBlank() && source.startsWith("$target\n"))

    fun interpolate(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    private fun smooth(start: Float, end: Float, progress: Float): Float {
        if (end == start) return if (progress < start) 0f else 1f
        val t = ((boundedProgress(progress) - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun motionProgress(progress: Float): Float = smooth(0.12f, 0.85f, progress)

    fun sourceWeight(progress: Float): Float {
        boundedProgress(progress)
        return 0f
    }

    fun targetWeight(progress: Float): Float {
        boundedProgress(progress)
        return 0f
    }

    fun overlayWeight(progress: Float): Float {
        boundedProgress(progress)
        return 1f
    }

    fun sourceTakeoverAlpha(progress: Float): Float = smooth(0.02f, 0.14f, progress)

    private fun boundedProgress(progress: Float): Float =
        if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
}
