package com.SplashScreenAdvanced.xposedmodule.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun item(key: Int, title: String, section: String = "Section", tab: String = "Tab", order: Int = key) =
        SettingsSearchItem(key, title, section, tab, order)

    @Test fun blankQueryFindsNothing() {
        val items = listOf(item(1, "Shrink icon"))
        assertTrue(SettingsSearch.rank("", items).isEmpty())
        assertTrue(SettingsSearch.rank("   ", items).isEmpty())
    }

    @Test fun titlePrefixThenTitleSubstringThenPlace() {
        val items = listOf(
            item(1, "Draw icon round corners"),
            item(2, "Icon pack"),
            item(3, "Quality", section = "Icon quality"),
            item(4, "Shrink icon"),
        )
        assertEquals(listOf(2, 1, 4, 3), SettingsSearch.rank("icon", items).map { it.key })
    }

    @Test fun pageNameListsTheWholePage() {
        val items = listOf(
            item(1, "Draw round corners", tab = "Icon"),
            item(2, "Replace source", tab = "Icon"),
            item(3, "Minimum duration", tab = "Display"),
        )
        assertEquals(listOf(1, 2), SettingsSearch.rank("icon", items).map { it.key })
    }

    @Test fun matchingIgnoresCaseAndSurroundingSpace() {
        val items = listOf(item(1, "Hide launcher icon"))
        assertEquals(listOf(1), SettingsSearch.rank("  LAUNCHER ", items).map { it.key })
    }

    @Test fun resultsKeepCatalogOrderInsideEachGroup() {
        val items = listOf(item(5, "b log", order = 30), item(6, "a log", order = 10), item(7, "c log", order = 20))
        assertEquals(listOf(6, 7, 5), SettingsSearch.rank("log", items).map { it.key })
    }

    @Test fun resultsAreCapped() {
        val items = (1..SettingsSearch.LIMIT + 10).map { item(it, "Row $it") }
        assertEquals(SettingsSearch.LIMIT, SettingsSearch.rank("row", items).size)
    }

    @Test fun orderFollowsPageThenSectionThenRegistration() {
        val icon = SettingsSearch.order(SettingsSection.IconShape, 40)
        val iconLater = SettingsSearch.order(SettingsSection.IconSource, 0)
        val background = SettingsSearch.order(SettingsSection.BackgroundColor, 0)
        val home = SettingsSearch.order(SettingsSection.Scope, 999)
        assertTrue(home < icon)
        assertTrue(icon < iconLater)
        assertTrue(iconLater < background)
    }
}
