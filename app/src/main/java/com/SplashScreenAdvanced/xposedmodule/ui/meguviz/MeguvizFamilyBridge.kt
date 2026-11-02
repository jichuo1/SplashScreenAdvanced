package com.SplashScreenAdvanced.xposedmodule.ui.meguviz

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.jichuo1.meguviz.android.window.InsetsSlot
import io.github.jichuo1.meguviz.android.window.InsetsValue
import io.github.jichuo1.meguviz.android.window.WindowSession
import io.github.jichuo1.meguviz.drawable.android.OwnedDrawableWrapper
import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe
import io.github.jichuo1.meguviz.drawable.model.FillSpec
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics
import io.github.jichuo1.meguviz.drawable.model.Length
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec
import io.github.jichuo1.meguviz.ui.ComponentType
import io.github.jichuo1.meguviz.ui.CreationEnvironment
import io.github.jichuo1.meguviz.ui.SessionState
import io.github.jichuo1.meguviz.ui.UiSession
import io.github.jichuo1.meguviz.ui.ViewKey

/** One owner for native view identity, window resources and drawable callbacks. */
class MeguvizFamilyBridge(private val activity: Activity) : AutoCloseable {
    private val ui = UiSession(CreationEnvironment(activity, resourceOwner = "ssa-settings"))
    private var window: WindowSession? = null
    private var installed = false
    private val ownedDrawables = mutableListOf<OwnedDrawableWrapper>()
    private val pendingInsets = mutableListOf<InsetsClaim>()
    private var closed = false

    val isActive: Boolean get() = !closed && ui.state == SessionState.ACTIVE

    fun install(root: View) {
        check(!closed && window == null) { "Meguviz content is already installed" }
        activity.setContentView(root)
        registerTree(root, "root")
        window = WindowSession.open(
            activity.window,
            activity,
            activity.packageName,
            WindowSession.Permission.OWN_WINDOW,
        )
        installed = true
        pendingInsets.forEach { it.bind(checkNotNull(window)) }
        pendingInsets.clear()
    }

    /**
     * Observes window insets on [view] through an exclusive slot owned by this window session, so the listener
     * is released with the session instead of leaking a raw listener. A claim made before [install] waits for it.
     * [onInsets] only reads the snapshot; a throwing callback closes the view scope.
     */
    fun claimInsets(view: View, typeMask: Int, onInsets: (InsetsValue) -> Unit) {
        if (closed) return
        val claim = InsetsClaim(view, typeMask, onInsets)
        val session = window
        if (session != null) claim.bind(session) else pendingInsets.add(claim)
    }

    private class InsetsClaim(val view: View, val typeMask: Int, val onInsets: (InsetsValue) -> Unit) {
        fun bind(session: WindowSession) {
            val scope = session.scope(view)
            scope.own(InsetsSlot.claimExclusive(scope).observe { onInsets(it.combined(typeMask)) })
        }
    }

    fun adopt(view: View, key: String) {
        if (installed && !closed) registerTree(view, key)
    }

    private fun registerTree(view: View, key: String) {
        registerView(view, key)
        if (view is ViewGroup) {
            repeat(view.childCount) { index ->
                val child = view.getChildAt(index)
                registerTree(child, "$key/${child.javaClass.simpleName}:$index")
            }
        }
    }

    private fun <V : View> registerView(view: V, key: String) {
        ui.borrow(view, ViewKey(key), ComponentType(view.javaClass.name, view.javaClass))
    }

    fun own(drawable: Drawable): Drawable = OwnedDrawableWrapper(drawable).also(ownedDrawables::add)

    fun rounded(color: Int, radiusDp: Float): Drawable = RecipeDrawable(
        DrawingRecipe(
            ShapeSpec.roundRect(Length.dp(radiusDp)),
            FillSpec.solid(color),
            null,
            LayoutMetrics.unspecified(),
        ),
        activity.resources.displayMetrics.density,
    )

    fun onStart() {
        if (closed) return
        if (ui.state == SessionState.DETACHED) ui.reattach()
        window?.setStarted(true)
    }

    fun onResume() {
        if (!closed) window?.setVisible(true)
    }

    fun onPause() {
        if (!closed) window?.setVisible(false)
    }

    fun onStop() {
        if (closed) return
        window?.setStarted(false)
        if (ui.state == SessionState.ACTIVE) ui.detach()
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingInsets.clear()
        try {
            window?.close()
            window = null
        } finally {
            try {
                ui.close()
            } finally {
                ownedDrawables.asReversed().forEach(OwnedDrawableWrapper::close)
                ownedDrawables.clear()
            }
        }
    }
}
