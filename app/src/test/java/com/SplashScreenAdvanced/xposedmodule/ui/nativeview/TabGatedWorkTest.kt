package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import com.SplashScreenAdvanced.xposedmodule.ui.settings.SettingsTab
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGatedWorkTest {
    /** A work body that counts how many times it is running, and how often it was started and cancelled. */
    private class Probe {
        val running = AtomicInteger()
        val starts = AtomicInteger()
        val cancels = AtomicInteger()
        suspend fun body() {
            starts.incrementAndGet()
            running.incrementAndGet()
            try {
                while (true) delay(5)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                cancels.incrementAndGet()
                throw cancelled
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private suspend fun await(what: String, condition: () -> Boolean) = withTimeout(2_000) {
        while (!condition()) delay(2)
    }.also { assertTrue(what, condition()) }

    private fun runGate(gate: TabGatedWork, block: suspend () -> Unit) = runBlocking(Dispatchers.Default) {
        val job = launch(start = CoroutineStart.UNDISPATCHED) { gate.run() }
        try { block() } finally { job.cancel() }
    }

    @Test
    fun workOfAnotherTabDoesNotRun() {
        val gate = TabGatedWork()
        val icon = Probe()
        gate.add(SettingsTab.Icon, icon::body)
        gate.shownTab.value = SettingsTab.Home
        runGate(gate) {
            delay(60)
            assertEquals(0, icon.starts.get())
        }
    }

    @Test
    fun workRunsWhileItsTabIsShownAndStopsWhenPagedAway() {
        val gate = TabGatedWork()
        val icon = Probe()
        gate.add(SettingsTab.Icon, icon::body)
        gate.shownTab.value = SettingsTab.Home
        runGate(gate) {
            gate.shownTab.value = SettingsTab.Icon
            await("started on its tab") { icon.running.get() == 1 }
            gate.shownTab.value = SettingsTab.Display
            await("cancelled when paged away") { icon.running.get() == 0 }
            assertEquals(1, icon.cancels.get())
            gate.shownTab.value = SettingsTab.Icon
            await("resumed on return") { icon.running.get() == 1 }
            assertEquals(2, icon.starts.get())
        }
    }

    @Test
    fun aStandalonePageRunsEverythingRegistered() {
        val gate = TabGatedWork()
        val icon = Probe()
        val module = Probe()
        gate.add(SettingsTab.Icon, icon::body)
        gate.add(SettingsTab.Module, module::body)
        runGate(gate) {
            await("both running with no shown tab") { icon.running.get() == 1 && module.running.get() == 1 }
        }
    }

    @Test
    fun workRegisteredAfterRunBeganIsPickedUp() {
        val gate = TabGatedWork()
        val icon = Probe()
        gate.shownTab.value = SettingsTab.Icon
        runGate(gate) {
            delay(30)
            gate.add(SettingsTab.Icon, icon::body)
            await("late registration starts on the shown tab") { icon.running.get() == 1 }
        }
    }

    @Test
    fun registeringAnotherTabsWorkDoesNotRestartTheRunningOne() {
        val gate = TabGatedWork()
        val icon = Probe()
        gate.add(SettingsTab.Icon, icon::body)
        gate.shownTab.value = SettingsTab.Icon
        runGate(gate) {
            await("started") { icon.running.get() == 1 }
            // A page built ahead of its visit registers its own work: the running tab must not notice.
            gate.add(SettingsTab.Module, Probe()::body)
            delay(60)
            assertEquals(1, icon.starts.get())
            assertEquals(0, icon.cancels.get())
        }
    }

    @Test
    fun clearStopsEverything() {
        val gate = TabGatedWork()
        val icon = Probe()
        gate.add(SettingsTab.Icon, icon::body)
        gate.shownTab.value = SettingsTab.Icon
        runGate(gate) {
            await("started") { icon.running.get() == 1 }
            gate.clear()
            await("stopped after clear") { icon.running.get() == 0 }
        }
    }
}
