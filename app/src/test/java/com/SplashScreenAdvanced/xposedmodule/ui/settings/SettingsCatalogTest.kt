package com.SplashScreenAdvanced.xposedmodule.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test fun dockFitsItsSlotsAndStartsAtHome() {
        assertTrue(SettingsTab.entries.size in 2..SettingsTab.MAX_TABS)
        assertEquals(SettingsTab.Home, SettingsTab.entries.first())
    }

    @Test fun everyDockPageHasAtLeastOneSection() {
        SettingsTab.entries.forEach { tab ->
            assertTrue("$tab has no section", SettingsSection.of(tab).isNotEmpty())
        }
    }

    @Test fun sectionsAreDeclaredGroupedByPageInDockOrder() {
        val pages = SettingsSection.entries.map { it.tab.ordinal }
        assertEquals("sections must follow dock order", pages.sorted(), pages)
    }

    @Test fun sectionTitlesAreDistinctWithinAPage() {
        SettingsTab.entries.forEach { tab ->
            val titles = SettingsSection.of(tab).map { it.titleRes }
            assertEquals("$tab repeats a section title", titles.distinct(), titles)
        }
    }

    @Test fun pageLabelsAreDistinct() {
        val labels = SettingsTab.entries.map { it.labelRes }
        assertEquals(labels.distinct(), labels)
    }
}
