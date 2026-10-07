package com.SplashScreenAdvanced.xposedmodule.data

import kotlinx.serialization.Serializable

/**
 * UI-independent page identity. Existing FairMemory tokens remain unchanged.
 */
@Serializable
sealed interface Route {
    @Serializable data object Main : Route
    @Serializable data object Empty : Route
    @Serializable data object About : Route
    @Serializable data object Basic : Route
    @Serializable data object Scope : Route
    @Serializable data object Icon : Route
    @Serializable data object Bottom : Route
    @Serializable data object Background : Route
    @Serializable data object Display : Route
    @Serializable data object Developer : Route

    @Serializable data object CustomScope : Route
    @Serializable data object IgnoreAppIcon : Route
    @Serializable data object HideIcon : Route
    @Serializable data object RemoveBranding : Route
    @Serializable data object BackgroundExcept : Route
    @Serializable data object BgIndividual : Route
    @Serializable data object MinDuration : Route
    @Serializable data object ForceSplash : Route

    @Serializable data class ColorPicker(val pkgName: String = "") : Route
}
