package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsTab
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Background work that belongs to one dock page and only matters while that page is on screen (a polled statistic, say).
 *
 * Dock pages are built ahead of their first visit, so registering work cannot mean starting it. [run] keeps exactly the
 * work of the shown tab going, cancels it when the user pages away, and also picks up work registered after [run] began.
 */
internal class TabGatedWork {
    private class Entry(val tab: SettingsTab, val work: suspend () -> Unit)

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    /** The dock tab on screen; null for a page that stands alone (a routed page), where everything registered is visible. */
    val shownTab = MutableStateFlow<SettingsTab?>(null)

    fun add(tab: SettingsTab, work: suspend () -> Unit) {
        entries.update { it + Entry(tab, work) }
    }

    fun clear() {
        entries.value = emptyList()
    }

    /** Runs until cancelled; the caller owns the lifetime (the activity's started window). */
    suspend fun run() {
        combine(entries, shownTab) { all, shown -> all.filter { shown == null || it.tab == shown } }
            .distinctUntilChanged()
            .collectLatest { active -> coroutineScope { active.forEach { launch { it.work() } } } }
    }
}
