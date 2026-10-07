package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import org.junit.Assert.*
import org.junit.Test

class NativeAppDraftTest {
    @Test fun selectionAndDurationRemainStagedUntilSave() {
        val draft = NativeAppDraft(setOf("com.a"), mapOf("com.a" to "250"))
        draft.setChecked("com.b", true)
        draft.setConfig("com.b", "600")
        assertTrue(draft.isDirty)
        assertEquals(setOf("com.a"), draft.snapshot().savedChecked)
        assertEquals(mapOf("com.a" to "250"), draft.snapshot().savedConfig)
        draft.markSaved()
        assertFalse(draft.isDirty)
        draft.setConfig("com.b", null)
        assertTrue(draft.isDirty)
    }

    @Test fun recreationKeepsTheEntryBaselineAndCurrentUnsavedValues() {
        val draft = NativeAppDraft(setOf("com.app_with_underscore"), mapOf("com.app_with_underscore" to "20"))
        draft.setChecked("com.new", true)
        draft.setConfig("com.app_with_underscore", "0")
        val recreated = NativeAppDraft(emptySet())
        recreated.restore(draft.snapshot())
        assertTrue(recreated.isDirty)
        assertEquals(draft.checked, recreated.checked)
        assertEquals(draft.config, recreated.config)
        recreated.discard()
        assertEquals(setOf("com.app_with_underscore"), recreated.checked)
        assertEquals(mapOf("com.app_with_underscore" to "20"), recreated.config)
        assertFalse(recreated.isDirty)
    }

    @Test fun snapshotsCannotBeChangedByLaterEdits() {
        val draft = NativeAppDraft(setOf("com.a", "com.b"), mapOf("com.a" to "1", "com.b" to "2"))
        val snapshot = draft.snapshot()
        draft.checked.clear()
        draft.config.clear()
        assertEquals(setOf("com.a", "com.b"), snapshot.checked)
        assertEquals(2, snapshot.config.size)
        draft.restore(snapshot)
        draft.setConfig("com.a", "9")
        assertEquals("1", snapshot.config["com.a"])
    }

    @Test fun reloadReplacesBothBaselineAndEditableState() {
        val draft = NativeAppDraft(setOf("com.old"), mapOf("com.old" to "200"))
        draft.setChecked("com.new", true)
        draft.reload(setOf("com.remote"), mapOf("com.remote" to "0"))
        assertFalse(draft.isDirty)
        assertEquals(setOf("com.remote"), draft.checked)
        assertEquals(mapOf("com.remote" to "0"), draft.config)
    }
}
