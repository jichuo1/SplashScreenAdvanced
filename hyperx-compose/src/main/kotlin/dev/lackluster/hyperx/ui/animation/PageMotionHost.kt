package dev.lackluster.hyperx.ui.animation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.coroutines.cancellation.CancellationException
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalPageMotion = compositionLocalOf<PageMotionController?> { null }
val LocalPageMotionKey = compositionLocalOf<Any?> { null }
val LocalPageActive = compositionLocalOf { true }

data class PageMotionEntry(val key: NavKey, val entry: NavEntry<NavKey>)

@Composable
fun rememberPageMotionController(popBackStack: (Any) -> Unit): PageMotionController {
    val scope = rememberCoroutineScope()
    val onPop = rememberUpdatedState(popBackStack)
    return remember(scope) { PageMotionController(scope, { onPop.value(it) }) }
}

/**
 * Pages stay in their Navigation 3 saveable scopes until popped. Only the top page and its
 * immediate background are drawn; hidden pages are lifecycle-capped and cannot receive back.
 */
@Composable
fun PageMotionHost(
    motion: PageMotionController,
    entries: List<PageMotionEntry>,
    modifier: Modifier = Modifier,
) {
    val currentKey = entries.last().key
    val density = LocalDensity.current
    val surface = MiuixTheme.colorScheme.surface
    val card = MiuixTheme.colorScheme.background
    val frame = remember { PageMotionFrameBuffer() }
    val bounds = remember { HostBounds() }
    SideEffect { motion.contentTravelPx = with(density) { PageEnterMotionSpec.CONTENT_TRAVEL_DP.dp.toPx() } }

    LaunchedEffect(currentKey) {
        val generation = motion.session.generation
        // Wait for actual title/row placement, not a guessed delay in milliseconds.
        withFrameNanos { }
        withFrameNanos { }
        motion.onDestinationPresented(currentKey, generation)
    }
    DisposableEffect(motion) {
        onDispose { motion.onHostDisposed() }
    }

    // Register before page content: a page's unsaved-changes handler keeps priority.
    PredictiveBackHandler(enabled = entries.size > 1) { events ->
        if (motion.beginPredictive()) {
            try {
                events.collect { motion.seekPredictive(it.progress) }
                motion.commitPredictive()
            } catch (cancelled: CancellationException) {
                motion.cancelPredictive()
                throw cancelled
            }
        }
    }

    Box(
        modifier.fillMaxSize().background(surface).onGloballyPositioned {
            bounds.rect = it.unclippedBoundsInWindow()
            motion.updateHostBounds(bounds.rect)
        }
    ) {
        entries.forEachIndexed { index, entry ->
            key(entry.entry.contentKey) {
                val foreground = index == entries.lastIndex
                val visible = foreground || (motion.isActive && index == entries.lastIndex - 1)
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (visible) 1f else 0f }
                        .then(if (foreground) Modifier else Modifier.clearAndSetSemantics {})
                ) {
                    if (foreground && motion.isActive) {
                        Canvas(Modifier.fillMaxSize()) {
                            motion.fillFrame(frame)
                            drawRoundRect(
                                color = lerp(card, surface, motion.expansion).copy(alpha = frame.surfaceAlpha),
                                topLeft = Offset(frame.left - bounds.rect.left, frame.top - bounds.rect.top),
                                size = Size((frame.right - frame.left).coerceAtLeast(0f), (frame.bottom - frame.top).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(frame.cornerRadiusPx),
                            )
                        }
                    }
                    val pageLayer = if (foreground) Modifier.graphicsLayer {
                        if (motion.isActive) {
                            motion.fillFrame(frame)
                            shape = MotionShape(
                                Rect(frame.left - bounds.rect.left, frame.top - bounds.rect.top,
                                    frame.right - bounds.rect.left, frame.bottom - bounds.rect.top),
                                frame.cornerRadiusPx,
                            )
                            clip = true
                            alpha = frame.contentAlpha
                            translationY = frame.contentTranslationYPx
                        } else {
                            clip = false
                            alpha = 1f
                            translationY = 0f
                        }
                    } else Modifier
                    MotionPageScope(entry.key, foreground) {
                        Box(pageLayer.fillMaxSize().pageMotionInput(motion, foreground)
                            .then(if (motion.inputBlocked) Modifier.clearAndSetSemantics {} else Modifier)) {
                            entry.entry.Content()
                        }
                    }
                }
            }
        }
        if (motion.overlayVisible) {
            val from = motion.source!!
            val to = motion.target!!
            // Two fixed text layouts blend font weight while their position/size share one progress.
            listOf(from, to).forEachIndexed { index, anchor ->
                Text(
                    text = anchor.text,
                    fontSize = with(density) { anchor.fontSizePx.toSp() },
                    fontWeight = anchor.fontWeight,
                    color = anchor.color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                        val progress = TitleMotionSpec.motionProgress(motion.expansion)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = TitleMotionSpec.interpolate(from.bounds.left, to.bounds.left, progress) - bounds.rect.left
                        translationY = TitleMotionSpec.interpolate(from.bounds.top, to.bounds.top, progress) - bounds.rect.top
                        val textSize = TitleMotionSpec.interpolate(from.fontSizePx, to.fontSizePx, progress)
                        scaleX = textSize / anchor.fontSizePx.coerceAtLeast(1f)
                        scaleY = scaleX
                        alpha = if (index == 0) 1f - progress else progress
                    },
                )
            }
        }
    }
}

@Composable
fun MotionPageScope(pageKey: Any, active: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent, pageKey) {
        object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
    }
    val activeState = rememberUpdatedState(active)
    fun updateLifecycle() {
        val cap = if (activeState.value) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        owner.registry.currentState = minOf(parent.lifecycle.currentState, cap)
    }
    DisposableEffect(parent, owner) {
        val observer = LifecycleEventObserver { _, _ -> updateLifecycle() }
        parent.lifecycle.addObserver(observer)
        onDispose {
            parent.lifecycle.removeObserver(observer)
            owner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }
    SideEffect { updateLifecycle() }
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalPageMotionKey provides pageKey,
        LocalPageActive provides active,
        content = content,
    )
}

/** Decide ownership on DOWN and keep it through UP, including when an animation finishes. */
internal fun Modifier.pageMotionInput(motion: PageMotionController, active: Boolean): Modifier =
    composed {
    val coordinates = remember { InputCoordinates() }
    this.onGloballyPositioned { coordinates.value = it }.pointerInput(motion, active) {
        awaitPointerEventScope {
            var blocked = false
            var gesture = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!gesture && event.changes.any { it.changedToDownIgnoreConsumed() }) {
                    gesture = true
                    val local = event.changes.first().position
                    val point = coordinates.value?.takeIf { it.isAttached }?.localToWindow(local)
                    blocked = !active || (motion.inputBlocked &&
                        (point == null || !motion.isBackTarget(point.x, point.y)))
                }
                if (blocked) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) { gesture = false; blocked = false }
            }
        }
    }
    }

internal fun LayoutCoordinates.unclippedBoundsInWindow(): Rect =
    Rect(positionInWindow(), size.toSize())

private class HostBounds { var rect = Rect.Zero }
private class InputCoordinates { var value: LayoutCoordinates? = null }

private class MotionShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))))
}
