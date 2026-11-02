package com.SplashScreenAdvanced.xposedmodule.utils

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.SplashScreenAdvanced.xposedmodule.BuildConfig

/**
 * Named trace sections for the settings app's own start-up and page work (Perfetto / systrace show them; the section
 * calls cost nothing while no trace is recording). Debug builds also log each duration, which is how the numbers in
 * performance notes are taken on a device without a profiler.
 */
object PerfTrace {
    private const val TAG = "SSAPerf"

    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        val start = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
        try {
            return block()
        } finally {
            Trace.endSection()
            if (BuildConfig.DEBUG) log(name, SystemClock.elapsedRealtimeNanos() - start)
        }
    }

    @PublishedApi
    internal fun log(name: String, nanos: Long) {
        Log.d(TAG, "$name ${nanos / 1_000}us")
    }
}
