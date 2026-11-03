package com.SplashScreenAdvanced.xposedmodule.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.SplashScreenAdvanced.xposedmodule.R

/**
 * Horizontal dock pages, in BIL order: one home page followed by function pages.
 * The dock is laid out for at most [MAX_TABS] slots. Icons are single-colour glyphs: the dock tints them,
 * which would flatten the two-tone tiles used by the route list into plain squares.
 */
enum class SettingsTab(
    @get:StringRes val titleRes: Int,
    @get:StringRes val labelRes: Int,
    @get:DrawableRes val iconRes: Int,
) {
    Home(R.string.app_name, R.string.home_tab_overview, R.drawable.ic_dock_home),
    Icon(R.string.icon_settings, R.string.icon_settings, R.drawable.ic_dock_icon),
    Background(R.string.background_settings, R.string.background_settings, R.drawable.ic_dock_background),
    Display(R.string.display_settings, R.string.display_settings, R.drawable.ic_dock_display),
    Module(R.string.home_tab_module, R.string.home_tab_module, R.drawable.ic_dock_module);

    companion object {
        const val MAX_TABS = 5
    }
}

/**
 * Titled groups inside a dock page. Declaration order is display order inside the tab.
 * Pages are composed from sections, and so is the standalone route page of each section's feature.
 */
enum class SettingsSection(val tab: SettingsTab, @get:StringRes val titleRes: Int) {
    Scope(SettingsTab.Home, R.string.custom_scope_settings),
    // Checked from the overview next to the module status, so the Module page keeps only how the module looks and behaves.
    HomeUpdate(SettingsTab.Home, R.string.update_settings),

    IconShape(SettingsTab.Icon, R.string.section_icon_shape),
    IconSource(SettingsTab.Icon, R.string.section_icon_source),
    IconEnhance(SettingsTab.Icon, R.string.section_icon_enhance),
    IconPerApp(SettingsTab.Icon, R.string.section_icon_per_app),

    BackgroundColor(SettingsTab.Background, R.string.section_background_color),
    BackgroundPerApp(SettingsTab.Background, R.string.section_background_per_app),
    BackgroundSystem(SettingsTab.Background, R.string.section_background_system),

    DisplayTiming(SettingsTab.Display, R.string.section_display_timing),
    DisplayBranding(SettingsTab.Display, R.string.section_display_branding),
    DisplayCompat(SettingsTab.Display, R.string.section_display_compat),

    ModuleAppearance(SettingsTab.Module, R.string.section_module_appearance),
    ModuleSystem(SettingsTab.Module, R.string.section_module_system),
    ModuleBackup(SettingsTab.Module, R.string.section_module_backup),
    /** Brand, links and the open-source licenses, which fold into one expandable row. */
    ModuleAbout(SettingsTab.Module, R.string.about);

    companion object {
        fun of(tab: SettingsTab): List<SettingsSection> = entries.filter { it.tab == tab }
    }
}
