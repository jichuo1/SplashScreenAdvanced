package com.SplashScreenAdvanced.xposedmodule.ui.meguviz

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.jichuo1.meguviz.android.runtime.core.FrameScheduler
import io.github.jichuo1.meguviz.android.runtime.core.FrameTick
import io.github.jichuo1.meguviz.android.window.InsetsSlot
import io.github.jichuo1.meguviz.android.window.InsetsValue
import io.github.jichuo1.meguviz.android.window.WindowSession
import io.github.jichuo1.meguviz.drawable.android.OwnedDrawableWrapper
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
    private val idleTasks = ArrayDeque<() -> Unit>()
    private var idleChannel: WindowSession.WindowFrameChannel? = null
    private var idleGate: () -> Boolean = { true }

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

    /**
     * Queues [task] for a later frame. The window's frame scheduler hands the channel one unit per frame and only while
     * the window is started, so a queue of page builds spreads over frames instead of landing in one. While [ready]
     * is false (a finger is down, a page is moving) the queue waits, and is asked again a moment later.
     */
    fun runWhenIdle(ready: () -> Boolean, task: () -> Unit) {
        val session = window
        if (closed || session == null) return
        idleGate = ready
        idleTasks.addLast(task)
        val channel = idleChannel ?: session.openFrameChannel(this, FrameScheduler.FrameWork { tick, allowance -> drainIdle(tick, allowance) }, 1)
            .also { idleChannel = it }
        channel.requestFrame()
    }

    private fun drainIdle(tick: FrameTick, allowance: Int): Int {
        val channel = idleChannel ?: return 0
        if (allowance <= 0 || idleTasks.isEmpty()) return 0
        if (!idleGate()) {
            activity.window?.decorView?.postDelayed({ if (!closed) channel.requestFrame() }, IDLE_RETRY_MS)
            return 0
        }
        idleTasks.removeFirst().invoke()
        if (idleTasks.isNotEmpty()) channel.requestFrame()
        return 1
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

    fun onStart() {
        if (closed) return
        if (ui.state == SessionState.DETACHED) ui.reattach()
        window?.setStarted(true)
    }

    fun onResume() {
        if (closed) return
        window?.setVisible(true)
        if (idleTasks.isNotEmpty()) idleChannel?.requestFrame()
    }

    fun onPause() {
        if (closed) return
        idleChannel?.cancelPending()
        window?.setVisible(false)
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
        idleTasks.clear()
        idleChannel?.close()
        idleChannel = null
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

    private companion object {
        const val IDLE_RETRY_MS = 120L
    }
}
