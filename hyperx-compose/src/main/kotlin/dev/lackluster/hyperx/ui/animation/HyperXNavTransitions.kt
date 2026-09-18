package dev.lackluster.hyperx.ui.animation

import androidx.compose.animation.core.CubicBezierEasing

/** Innocent Lab's motion curves, shared by entry, ordinary close and gesture continuation. */
internal object HyperXNavTransitions {
    val EnterEasing = CubicBezierEasing(
        PageEnterMotionSpec.ENTER_EASING_X1, PageEnterMotionSpec.ENTER_EASING_Y1,
        PageEnterMotionSpec.ENTER_EASING_X2, PageEnterMotionSpec.ENTER_EASING_Y2,
    )
    val CloseEasing = CubicBezierEasing(
        PageEnterMotionSpec.CLOSE_EASING_X1, PageEnterMotionSpec.CLOSE_EASING_Y1,
        PageEnterMotionSpec.CLOSE_EASING_X2, PageEnterMotionSpec.CLOSE_EASING_Y2,
    )
    val CommitEasing = CubicBezierEasing(
        PageEnterMotionSpec.COMMIT_EASING_X1, PageEnterMotionSpec.COMMIT_EASING_Y1,
        PageEnterMotionSpec.COMMIT_EASING_X2, PageEnterMotionSpec.COMMIT_EASING_Y2,
    )
}
