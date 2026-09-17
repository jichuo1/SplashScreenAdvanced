package dev.lackluster.hyperx.ui.animation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalPageMotion = compositionLocalOf<PageMotionController?> { null }

@Composable
fun rememberPageMotionController(popBackStack: () -> Unit): PageMotionController {
    val scope = rememberCoroutineScope()
    return remember(scope) { PageMotionController(scope, popBackStack) }
}

@Composable
fun PageMotionHost(
    motion: PageMotionController,
    backStackSize: Int,
    currentKey: NavKey?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val surface = MiuixTheme.colorScheme.surface
    val card = MiuixTheme.colorScheme.background
    val outline = MiuixTheme.colorScheme.outline
    motion.contentTravelPx = with(density) { PageEnterMotionSpec.CONTENT_TRAVEL_DP.dp.toPx() }

    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(currentKey) {
        withFrameNanos { }
        if (currentKey != null) motion.onDestinationPresented()
    }

    PredictiveBackHandler(enabled = backStackSize > 1) { events: Flow<BackEventCompat> ->
        if (!motion.beginPredictive()) {
            throw CancellationException()
        }
        try {
            events.collect { event ->
                motion.seekPredictive(event.progress)
            }
            motion.commitPredictive()
        } catch (cancelled: CancellationException) {
            motion.cancelPredictive()
            throw cancelled
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .onGloballyPositioned { coords ->
                hostCoordinates = coords
                motion.expandedBounds = coords.boundsInWindow()
            }
    ) {
        val frame = motion.frame
        val localFrame = frame?.let { hostCoordinates?.let { coords -> it.toLocal(coords) } }
        val clipActive = motion.isActive && localFrame != null && motion.expansion < 0.999f

        if (clipActive && localFrame != null && frame != null) {
            val chrome = PageEnterMotionSpec.collapsedChromeFraction(motion.expansion)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val color = lerp(card, surface, motion.expansion)
                val radius = frame.cornerRadiusPx
                val roundRect = RoundRect(
                    left = localFrame.left,
                    top = localFrame.top,
                    right = localFrame.right,
                    bottom = localFrame.bottom,
                    cornerRadius = CornerRadius(radius, radius)
                )
                val path = Path().apply { addRoundRect(roundRect) }
                drawPath(
                    path = path,
                    color = color.copy(
                        alpha = color.alpha * PageEnterMotionSpec.transitionSurfaceAlpha(
                            motion.expansion,
                            PageEnterMotionSpec.SURFACE_HANDOFF
                        )
                    )
                )
                if (chrome > 0.01f && outline.alpha > 0f) {
                    drawPath(
                        path = path,
                        color = outline.copy(alpha = outline.alpha * chrome),
                        style = Stroke(width = density.run { 1.dp.toPx() })
                    )
                }
            }
        }

        val pageModifier = if (clipActive && localFrame != null && frame != null) {
            Modifier
                .graphicsLayer {
                    alpha = frame.contentAlpha
                    translationY = frame.contentTranslationYPx
                }
                .clip(MorphShape(localFrame, frame.cornerRadiusPx))
        } else {
            Modifier
        }

        Box(modifier = pageModifier.fillMaxSize()) {
            content()
        }

        val source = motion.source
        val target = motion.target
        if (motion.overlayVisible && source != null && target != null && frame != null && hostCoordinates != null) {
            val origin = hostCoordinates!!.localToWindow(Offset.Zero)
            val progress = TitleMotionSpec.motionProgress(motion.expansion)
            val x = TitleMotionSpec.interpolate(source.bounds.left, target.bounds.left, progress) - origin.x
            val y = TitleMotionSpec.interpolate(source.bounds.top, target.bounds.top, progress) - origin.y
            val size = TitleMotionSpec.interpolate(source.fontSizePx, target.fontSizePx, progress)
            val color = lerp(source.color, target.color, progress)
            val scale = size / target.fontSizePx.coerceAtLeast(1f)
            Text(
                text = target.text,
                color = color,
                fontSize = with(density) { target.fontSizePx.toSp() },
                fontWeight = target.fontWeight,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        alpha = TitleMotionSpec.sourceTakeoverAlpha(motion.expansion)
                    }
            )
        }

        if (motion.inputBlocked) {
            val topGuard = TopAppBarDefaults.CollapsedHeight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .offset(y = topGuard)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
fun pageMotionToolbarTitleColor(title: String, default: Color = MiuixTheme.colorScheme.onSurface): Color {
    val motion = LocalPageMotion.current
    return if (motion?.isHidingTargetTitle(title) == true) Color.Transparent else default
}

@Composable
fun PageMotionTargetProbe(
    title: String,
    titlePadding: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MiuixTheme.colorScheme.onSurface,
) {
    val motion = LocalPageMotion.current ?: return
    val density = LocalDensity.current
    Text(
        text = title,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = Color.Transparent,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .offset(y = TopAppBarDefaults.CollapsedHeight)
            .offset(x = titlePadding)
            .onGloballyPositioned { coords ->
                motion.reportTarget(
                    PageMotionAnchor(
                        text = title,
                        bounds = coords.boundsInWindow(),
                        rowBounds = coords.boundsInWindow(),
                        fontSizePx = with(density) { fontSize.toPx() },
                        color = color,
                        fontWeight = fontWeight,
                    )
                )
            }
    )
}

private class MorphShape(
    private val rect: Rect,
    private val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = rect.left.coerceIn(0f, size.width)
        val top = rect.top.coerceIn(0f, size.height)
        val right = rect.right.coerceIn(left, size.width)
        val bottom = rect.bottom.coerceIn(top, size.height)
        val corner = radius.coerceAtLeast(0f)
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                cornerRadius = CornerRadius(corner, corner)
            )
        )
    }
}

private fun PageMotionFrameBuffer.toLocal(coords: LayoutCoordinates): Rect {
    val origin = coords.localToWindow(Offset.Zero)
    return Rect(left - origin.x, top - origin.y, right - origin.x, bottom - origin.y)
}
