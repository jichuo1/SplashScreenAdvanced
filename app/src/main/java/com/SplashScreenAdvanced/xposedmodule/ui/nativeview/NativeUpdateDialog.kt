package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.app.Dialog
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isGone
import androidx.core.view.isNotEmpty
import androidx.core.graphics.ColorUtils
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker.ReleaseInfo
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesSourcePolicy
import com.lumen.coacervation.engine.motion.modal.LumenModalStyle

/** Only the announcement scrolls: the title and both actions always belong to the fixed card. */
internal fun NativePageUi.showUpdateDialog(
    release: ReleaseInfo,
    onDismissed: () -> Unit,
    onUpdate: () -> Unit
): Dialog {
    val dialog = Dialog(activity)
    val container = activity.modals.createContainer()
    // Dense release text needs a readable backing even when the dialog cannot sample another window.
    val backing = GradientDrawable().apply {
        cornerRadius = LumenModalStyle().cornerRadiusDp * activity.resources.displayMetrics.density
        setColor(ColorUtils.setAlphaComponent(palette.surface, 235))
    }
    container.background = LayerDrawable(arrayOf(backing, checkNotNull(container.background)))
    val name = release.displayName + if (release.prerelease) " " + string(R.string.update_prerelease_badge) else ""
    container.addView(text(string(R.string.update_new_version, name), 20f, bold = true).apply {
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
    }, LinearLayout.LayoutParams(-1, -2))
    val source = ReleaseNotesSourcePolicy.prepare(release.releaseNotes)
    val notes = releaseNotes(source.markdown).apply {
        setPadding(0, dp(12), 0, dp(12))
        if (source.truncated || release.releaseNotesTruncated) addView(text(string(R.string.update_notes_truncated), 13f, secondary = true))
    }
    if (notes.isNotEmpty()) container.addView(UpdateNotesScroll(activity, container, dp(16)).apply {
        id = R.id.native_update_notes
        clipChildren = true
        clipToPadding = true
        addView(notes, android.widget.FrameLayout.LayoutParams(-1, -2))
    }, LinearLayout.LayoutParams(-1, -2))
    val footer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
    }
    var closing = false
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        activity.modals.dismiss(dialog, container) { onDismissed(); after() }
    }
    footer.addView(button(string(R.string.update_view_release)) { close(onUpdate) }.apply {
        id = R.id.native_update_action
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
    }, LinearLayout.LayoutParams(-1, -2))
    footer.addView(button(string(R.string.button_cancel)) { close() }.apply {
        id = R.id.native_update_cancel
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    container.addView(footer, LinearLayout.LayoutParams(-1, -2))
    val metrics = activity.windowManager.currentWindowMetrics
    val safe = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    val width = minOf(dp(560), metrics.bounds.width() - safe.left - safe.right - dp(48)).coerceAtLeast(1)
    // Explicit width avoids wrapping each action to its label; the presenter owns surface and back dismissal.
    activity.modals.present(dialog, container, preferredWidth = width, onBackDismiss = onDismissed)
    return dialog
}

// This private viewport is created programmatically with its owning card; it is never inflated.
@SuppressLint("ViewConstructor")
private class UpdateNotesScroll(
    context: Context,
    private val card: LinearLayout,
    private val edgeMargin: Int
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val activity = context as NativeSettingsActivity
        val metrics = activity.windowManager.currentWindowMetrics
        val safe = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val available = metrics.bounds.height() - safe.top - safe.bottom - edgeMargin * 2
        // Measure fixed siblings before reserving a viewport, including wrapped labels at large font scales.
        var fixed = card.paddingTop + card.paddingBottom
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            if (child === this || child.isGone) continue
            child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val params = child.layoutParams as LinearLayout.LayoutParams
            fixed += child.measuredHeight + params.topMargin + params.bottomMargin
        }
        var limit = (available - fixed).coerceAtLeast(0)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            limit = minOf(limit, MeasureSpec.getSize(heightMeasureSpec))
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }
}
