package com.SplashScreenAdvanced.xposedmodule.fairmemory

import com.SplashScreenAdvanced.xposedmodule.data.Route
import dev.lackluster.hyperx.navigation.HyperXRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FairMemoryProtocolTest {
    @Test
    fun kindPrefersKillOverTrimWhenBothPresent() {
        assertEquals(
            FairMemoryProtocol.Kind.Kill,
            FairMemoryProtocol.kindOf(FairMemoryProtocol.ACTION_KILL, "trim")
        )
    }

    @Test
    fun kindReadsCommonActionWhenIntentActionMissing() {
        assertEquals(FairMemoryProtocol.Kind.Trim, FairMemoryProtocol.kindOf(null, "TRIM"))
        assertEquals(FairMemoryProtocol.Kind.Kill, FairMemoryProtocol.kindOf("", "Kill"))
        assertEquals(FairMemoryProtocol.Kind.Unknown, FairMemoryProtocol.kindOf(null, null))
    }

    @Test
    fun sessionTokensRoundTripKnownRoutes() {
        val keys = listOf(
            HyperXRoute.Main,
            Route.Basic,
            Route.Background,
            Route.ColorPicker("com.example.app"),
            Route.ColorPicker(""),
            Route.MinDuration,
        )
        for (key in keys) {
            val restored = parseFairMemoryToken(key.toFairMemoryToken())
            assertEquals(key, restored)
        }
    }

    @Test
    fun restoreBackStackInsertsMainUnderDetailPage() {
        val stack = restoreBackStackFromToken(Route.Icon.toFairMemoryToken())
        assertEquals(listOf(HyperXRoute.Main, Route.Icon), stack)
        assertEquals(listOf(HyperXRoute.Main), restoreBackStackFromToken("Main"))
        assertEquals(listOf(HyperXRoute.Main), restoreBackStackFromToken(null))
        assertEquals(listOf(HyperXRoute.Main), restoreBackStackFromToken("not-a-route"))
    }

    @Test
    fun resultCodesMatchDocument() {
        assertEquals(0, FairMemoryProtocol.RESULT_OK)
        assertEquals(1, FairMemoryProtocol.RESULT_FAILED)
        assertEquals(1000, FairMemoryProtocol.NOTIFY_PHYSICAL)
        assertEquals(2000, FairMemoryProtocol.NOTIFY_JAVA_HEAP)
        assertTrue(FairMemoryProtocol.ACTION_TRIM.endsWith(".TRIM"))
        assertTrue(FairMemoryProtocol.ACTION_KILL.endsWith(".KILL"))
    }
}
