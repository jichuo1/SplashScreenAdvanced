package com.SplashScreenAdvanced.xposedmodule.ui.page.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route

/**
 * 模块首页状态显示类型的枚举类
 *
 * @param cardBackground 状态关联的背景颜色。
 * @param stateIconRes 状态关联的图标的 drawable 资源 ID
 * @param stateTextRes 状态关联的文本的 string 资源 ID
 */
enum class ModuleStatusType(
    @get:ColorRes val cardBackground: Int,
    @get:DrawableRes val stateIconRes: Int,
    @get:StringRes val stateTextRes: Int
) {
    ACTIVE_NO_NEED_RESTART(R.color.topCardBackground, R.drawable.ic_success, R.string.module_is_active),
    ACTIVE_ANDROID_RESTART(R.color.topWarningCardBackground, R.drawable.ic_warn, R.string.module_is_updated_restart_phone_needed),
    ACTIVE_SYSTEM_UI_RESTART(R.color.topWarningCardBackground, R.drawable.ic_warn, R.string.module_is_updated_restart_phone_needed),
    INACTIVE(R.color.gray, R.drawable.ic_warn, R.string.module_is_not_active)
}

/**
 * 模块主页设置项资源的枚举类
 *
 * @property iconRes 图标资源的 drawable ID，用于在 UI 中显示设置项的图标
 * @property stringRes 标题资源的 string ID，用于在 UI 中显示设置项的名称
 * @property navigateTo 导航目标。如果为 null，则不执行导航操作。
 */
enum class ModulePreferenceRes(
    @get:DrawableRes val iconRes: Int,
    @get:StringRes val stringRes: Int,
    val navigateTo: Route? = null,
) {
    BasicSettings(R.drawable.ic_setting, R.string.basic_settings, Route.Basic),
    CustomScopeSettings(R.drawable.ic_app, R.string.custom_scope_settings, Route.Scope),
    IconSettings(R.drawable.ic_picture, R.string.icon_settings, Route.Icon),
    BottomSettings(R.drawable.ic_bottom, R.string.bottom_settings, Route.Bottom),
    BackgroundSettings(R.drawable.ic_color, R.string.background_settings, Route.Background),
    DisplaySettings(R.drawable.ic_monitor, R.string.display_settings, Route.Display),
    DevSettings(R.drawable.ic_lab, R.string.dev_settings, Route.Developer),
    About(R.drawable.ic_info, R.string.about, Route.About)
}
