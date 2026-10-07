package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executors

class HookCallScopeTest {
    @Test
    fun nestedFailureRestoresOuterSession() {
        val scope = HookCallScope<String>()
        scope.enter("app A")
        try {
            scope.enter("app B")
            try {
                assertEquals("app B", scope.current)
                throw IllegalStateException("host failure")
            } catch (_: IllegalStateException) {
                // 宿主异常后，仍必须走 finally 清理。
            } finally {
                scope.exit()
            }
            assertEquals("app A", scope.current)
        } finally {
            scope.exit()
        }
        assertNull(scope.current)
    }

    @Test
    fun workerDoesNotSeeCallerAndReusedThreadIsClean() {
        val scope = HookCallScope<String>()
        val worker = Executors.newSingleThreadExecutor()
        try {
            scope.enter("caller")
            worker.submit {
                assertNull(scope.current)
                scope.enter("worker")
                assertEquals("worker", scope.current)
                scope.exit()
            }.get()
            worker.submit { assertNull(scope.current) }.get()
            assertEquals("caller", scope.current)
        } finally {
            scope.exit()
            worker.shutdownNow()
        }
    }

    @Test
    fun emptyInnerSessionMasksOuterIdentity() {
        val scope = HookCallScope<List<String>>()
        scope.enter(listOf("app A"))
        scope.enter(emptyList())
        assertEquals(emptyList<String>(), scope.current)
        scope.exit()
        assertEquals(listOf("app A"), scope.current)
        scope.exit()
    }
}
