package com.SplashScreenAdvanced.xposedmodule.fairmemory

import androidx.navigation3.runtime.NavKey
import com.SplashScreenAdvanced.xposedmodule.data.Route
import dev.lackluster.hyperx.navigation.HyperXRoute

private const val COLOR_PICKER_PREFIX = "ColorPicker:"

/**
 * 把导航键压成可落盘的短 token, 查杀后再打开时恢复到被杀前的页面。
 *
 * 只记栈顶: 设置页没有必须成对出现的中间页, `Main + 栈顶` 足够回得去。
 */
fun NavKey.toFairMemoryToken(): String = when (this) {
    is HyperXRoute.Main -> "Main"
    is HyperXRoute.Empty -> "Empty"
    is Route.About -> "About"
    is Route.Basic -> "Basic"
    is Route.Scope -> "Scope"
    is Route.Icon -> "Icon"
    is Route.Bottom -> "Bottom"
    is Route.Background -> "Background"
    is Route.Display -> "Display"
    is Route.Developer -> "Developer"
    is Route.CustomScope -> "CustomScope"
    is Route.IgnoreAppIcon -> "IgnoreAppIcon"
    is Route.HideIcon -> "HideIcon"
    is Route.RemoveBranding -> "RemoveBranding"
    is Route.BackgroundExcept -> "BackgroundExcept"
    is Route.BgIndividual -> "BgIndividual"
    is Route.MinDuration -> "MinDuration"
    is Route.ForceSplash -> "ForceSplash"
    is Route.ColorPicker -> COLOR_PICKER_PREFIX + pkgName
    else -> this::class.qualifiedName ?: "Main"
}

fun parseFairMemoryToken(token: String): NavKey? = when (token) {
    "Main" -> HyperXRoute.Main
    "Empty" -> HyperXRoute.Empty
    "About" -> Route.About
    "Basic" -> Route.Basic
    "Scope" -> Route.Scope
    "Icon" -> Route.Icon
    "Bottom" -> Route.Bottom
    "Background" -> Route.Background
    "Display" -> Route.Display
    "Developer" -> Route.Developer
    "CustomScope" -> Route.CustomScope
    "IgnoreAppIcon" -> Route.IgnoreAppIcon
    "HideIcon" -> Route.HideIcon
    "RemoveBranding" -> Route.RemoveBranding
    "BackgroundExcept" -> Route.BackgroundExcept
    "BgIndividual" -> Route.BgIndividual
    "MinDuration" -> Route.MinDuration
    "ForceSplash" -> Route.ForceSplash
    else -> if (token.startsWith(COLOR_PICKER_PREFIX)) {
        Route.ColorPicker(token.removePrefix(COLOR_PICKER_PREFIX))
    } else {
        null
    }
}

fun restoreBackStackFromToken(token: String?): List<NavKey> {
    val key = token?.let { parseFairMemoryToken(it) } ?: return listOf(HyperXRoute.Main)
    if (key is HyperXRoute.Main) return listOf(HyperXRoute.Main)
    return listOf(HyperXRoute.Main, key)
}
