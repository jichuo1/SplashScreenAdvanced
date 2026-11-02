package com.SplashScreenAdvanced.xposedmodule.ui.settings

/** One searchable row, already resolved to display strings. [order] is the position in the catalog. */
data class SettingsSearchItem(
    val key: Int,
    val title: String,
    val section: String,
    val tab: String,
    val order: Int,
)

object SettingsSearch {
    const val LIMIT = 40

    /** Sort key that keeps results in dock order regardless of which pages were built first. */
    fun order(section: SettingsSection, sequence: Int): Int =
        section.tab.ordinal * 1_000_000 + section.ordinal * 1_000 + sequence.coerceIn(0, 999)

    /**
     * Title matches come first (prefix, then substring); rows whose section or page name matches
     * follow, so "icon" lists the whole Icon page. Each group keeps catalog order.
     */
    fun rank(query: String, items: List<SettingsSearchItem>): List<SettingsSearchItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val byOrder = items.sortedBy { it.order }
        val prefix = byOrder.filter { it.title.startsWith(needle, ignoreCase = true) }
        val titled = byOrder.filter { it.title.contains(needle, ignoreCase = true) && it !in prefix }
        val placed = byOrder.filter {
            it !in prefix && it !in titled &&
                (it.section.contains(needle, ignoreCase = true) || it.tab.contains(needle, ignoreCase = true))
        }
        return (prefix + titled + placed).take(LIMIT)
    }
}
