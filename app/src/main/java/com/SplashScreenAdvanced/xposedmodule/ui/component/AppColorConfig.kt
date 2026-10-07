package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.Context
import com.SplashScreenAdvanced.xposedmodule.R
import androidx.core.graphics.toColorInt
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.BGColorModes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ChangeBGColorTypes
import com.SplashScreenAdvanced.xposedmodule.utils.IconPackManager
import com.SplashScreenAdvanced.xposedmodule.utils.drawable2Bitmap
import com.SplashScreenAdvanced.xposedmodule.utils.getBgColor
import com.SplashScreenAdvanced.xposedmodule.utils.toMap

private const val COLOR_PICKER_ICON_SIZE = 96
internal class AppColorConfig(
    realPackageName: String?,
    private val context: Context,
    private val store: GlobalPreferencesRepository
) {
    private val pm = context.packageManager

    val isConfiguringOverallBGColor = realPackageName.isNullOrBlank()

    val packageName: String = realPackageName.takeIf { !it.isNullOrBlank() } ?: context.packageName
    val appName = if (isConfiguringOverallBGColor) {
        context.getString(R.string.set_custom_bg_color)
    } else {
        runCatching { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() }.getOrDefault(packageName)
    }

    val appIcon = runCatching {
        (IconPackManager(context, store.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME))
            .getIconByPackageName(packageName) ?: pm.getApplicationIcon(packageName)).drawable2Bitmap(COLOR_PICKER_ICON_SIZE)
    }.getOrElse { pm.getApplicationIcon(context.packageName).drawable2Bitmap(COLOR_PICKER_ICON_SIZE) }
    private var cachedIconBgLight: Int? = null
    private var cachedIconBgDark: Int? = null
    var defaultColorLight = processDefaultBGColor(false)
    var defaultColorDark = processDefaultBGColor(true)

    /**
     * 获取默认背景颜色
     * @param isDark 是否为暗色
     */
    fun getDefaultBGColor(isDark: Boolean): Int = if (isDark) {
        defaultColorDark
    } else {
        defaultColorLight
    }

    /**
     * 通过读取 Prefs 来获取已保存的背景颜色
     */
    private fun processDefaultBGColor(isDark: Boolean): Int {
        val colorValue = when {
            isConfiguringOverallBGColor && isDark -> {
                store.get(Preferences.Background.OVERALL_BG_COLOR_NIGHT)
            }

            isConfiguringOverallBGColor -> {
                store.get(Preferences.Background.OVERALL_BG_COLOR)
            }

            !isConfiguringOverallBGColor && isDark -> {
                store.get(Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK)
                    .toMap()[packageName].takeIf { !it.isNullOrBlank() }
            }

            !isConfiguringOverallBGColor -> {
                store.get(Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP)
                    .toMap()[packageName].takeIf { !it.isNullOrBlank() }
            }

            else -> null
        }

        val color = colorValue?.toColorInt()
            ?: effectiveGlobalBGColor(isDark)

        return color
    }

    /**
     * 未单独配置时的默认色: 镜像当前全局背景设置下该应用实际会显示的颜色,
     * 使单独配置的默认值与全局表现一致 (对应 BgHookHandler 取色逻辑)
     *
     * @param isDark 是否暗色, 对应取色页浅色/暗色分页
     */
    fun effectiveGlobalBGColor(isDark: Boolean): Int {
        // 浅色或深色由 BG_COLOR_MODE 决定
        val isLight = when (store.get(Preferences.Background.BG_COLOR_MODE)) {
            BGColorModes.LightColor.ordinal -> true
            BGColorModes.DarkColor.ordinal -> false
            else -> !isDark // FollowSystem
        }
        return when (store.get(Preferences.Background.CHANG_BG_COLOR_TYPE)) {
            // 继承全局自定义色
            ChangeBGColorTypes.FromCustom.ordinal -> {
                val value = store.get(
                    if (isDark) Preferences.Background.OVERALL_BG_COLOR_NIGHT
                    else Preferences.Background.OVERALL_BG_COLOR
                )
                value.takeIf { it.isNotBlank() }?.toColorInt() ?: iconBgColor(isLight)
            }
            // 继承系统 Monet 色, 取不到时回退图标主色
            ChangeBGColorTypes.FromMonet.ordinal -> runCatching {
                context.resources.getColor(
                    if (isLight) android.R.color.system_primary_container_light
                    else android.R.color.system_surface_dark,
                    context.theme
                )
            }.getOrDefault(iconBgColor(isLight))
            // 从图标取色 / 不改背景: 回退图标主色
            else -> iconBgColor(isLight)
        }
    }

    private fun iconBgColor(isLight: Boolean): Int {
        return if (isLight) {
            cachedIconBgLight ?: appIcon.getBgColor(true).also { cachedIconBgLight = it }
        } else {
            cachedIconBgDark ?: appIcon.getBgColor(false).also { cachedIconBgDark = it }
        }
    }
    fun reloadDefaults() { defaultColorLight = processDefaultBGColor(false); defaultColorDark = processDefaultBGColor(true) }
}
