package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.currentPackageName
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.getMapPrefs
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.hook.utils.ReflectCache
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.BGColorModes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ChangeBGColorTypes
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils.isHyperOS
import com.SplashScreenAdvanced.xposedmodule.utils.drawableDominantColor
import com.SplashScreenAdvanced.xposedmodule.utils.isDarkMode
import com.SplashScreenAdvanced.xposedmodule.wrapper.SplashScreenViewBuilderWrapper

/**
 * 此对象用于处理 背景 Hook
 */
object BgHookHandler : BaseHookHandler() {
    private var mTmpAttrsInstance: Any? = null

    /**
     * 重置当前应用的属性
     */
    fun resetCache() {
        mTmpAttrsInstance = null
    }

    /** 开始 Hook */
    override fun onHook() {
        SystemUIHooker.Members.getBGColorFromCache.addAfterHook {
            mTmpAttrsInstance = instance?.let { ReflectCache.getField<Any>(it, "mTmpAttrs") }
        }
        SystemUIHooker.Members.build_SplashScreenViewBuilder.addBeforeHook {
            val builder = SplashScreenViewBuilderWrapper.getInstance(instance!!)

            // 设置背景颜色
            getColor()?.let { color ->
                builder.setBackgroundColor(color)
                // overlay 存在时 build() 用它整体替换 view 背景, 背景色被完全遮盖
                // (OneUI 对 suggestType==4 的启动画面传应用 windowBackground drawable)
                builder.setOverlayDrawable(null)
            }
        }

        // ---- 背景色的其余出口 (windowless / shell-transition 路径, OneUI 8.5 实测) ----

        // startingSurface 由 drawThemeBGColor() 直接涂主题色; 末位 int 参为背景色
        SystemUIHooker.Members.drawThemeBGColor.addBeforeHook {
            getColor()?.let { color ->
                args.indices.lastOrNull { args[it] is Int }?.let {
                    args(it).set(color)
                    printLog { "drawThemeBGColor(): force bg color on starting surface" }
                }
            }
        }

        // 任务级背景色 (WMS/转场在 view 挂上前先涂 surface): 调用点在遮罩流程之前,
        // 不能用 isHooking 门控, 包名从 TaskInfo 实参里取
        SystemUIHooker.Members.estimateTaskBackgroundColor.addAfterHook({ true }) {
            taskPackageName(args)?.takeUnless { GenerateHookHandler.isExcept(it) }
                ?.let { getColor(it) }
                ?.let {
                    result = it
                    printLog { "estimateTaskBackgroundColor(): override task bg color" }
                }
        }
        SystemUIHooker.Members.getBackgroundColor_StartingSurface.addAfterHook({ true }) {
            taskPackageName(args)?.takeUnless { GenerateHookHandler.isExcept(it) }
                ?.let { getColor(it) }
                ?.let {
                    result = it
                    printLog { "getBackgroundColor(): override task bg color" }
                }
        }

        // 预加载遮罩复用门: 预建 view 在 hook 静默期构建, 复用时全部定制失效;
        // 该包有任何视觉定制需求时禁用复用, 强制走 makeSplashScreenContentView 现建
        SystemUIHooker.Members.canUseContext_PreloadData.addAfterHook({ true }) {
            if (result as? Boolean != true) return@addAfterHook
            val pkg = taskPackageName(args) ?: return@addAfterHook
            if (needsFreshSplash(pkg)) {
                result = false
                printLog { "canUseContext(): disable preload reuse for $pkg (customization active)" }
            }
        }
    }

    /** 从 TaskInfo/RunningTaskInfo 实参提取包名 (task 级查询点在遮罩流程之前, currentPackageName 不可靠) */
    private fun taskPackageName(args: Array<Any?>): String? {
        val info = args.getOrNull(0) ?: return null
        val activityInfo = (info as? ActivityInfo)
            ?: ReflectCache.getField<ActivityInfo>(info, "topActivityInfo")
            ?: ReflectCache.getField<ActivityInfo>(info, "targetActivityInfo")
        return activityInfo?.packageName
    }

    /** 该包是否有需要现建遮罩的视觉定制 (背景色或图标处理) */
    private fun needsFreshSplash(packageName: String): Boolean {
        if (packageName.isEmpty() || GenerateHookHandler.isExcept(packageName)) return false
        if (packageName in prefs.get(Preferences.AppList.BG_EXCEPT_LIST)) return false
        val individual = getMapPrefs(Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP).keys +
                getMapPrefs(Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK).keys
        if (packageName in individual) return true
        if (prefs.get(Preferences.Background.CHANG_BG_COLOR_TYPE) != 0) return true
        return iconCustomized()
    }

    private fun iconCustomized(): Boolean = listOf(
        Preferences.Icon.REPLACE_TO_EMPTY_SPLASH_SCREEN,
        Preferences.Icon.ENABLE_DEFAULT_STYLE,
        Preferences.Icon.ENABLE_HIDE_SPLASH_SCREEN_ICON,
        Preferences.Icon.ENABLE_REPLACE_ICON,
        Preferences.Icon.ENABLE_USE_MIUI_LARGE_ICON,
        Preferences.Icon.ENABLE_REMOVE_ICON_STROKE,
        Preferences.Icon.ENABLE_ADD_ICON_BLUR_BG,
    ).any { prefs.get(it) } ||
            prefs.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME) != "None" ||
            prefs.get(Preferences.Icon.SHRINK_ICON) != 0

    /**
     * 此处实现功能：
     * - 替换背景颜色
     * - 单独配置应用背景颜色
     *
     * @param packageName 目标应用包名; 遮罩流程内调用走默认的 [currentPackageName],
     *   遮罩流程外的任务级查询点需显式传入
     */
    private fun getColor(packageName: String = currentPackageName): Int? {
        val context = appContext ?: return null
        val isDarkMode = context.isDarkMode
        val bgColorMode = prefs.get(Preferences.Background.BG_COLOR_MODE)
        val bgColorType = prefs.get(Preferences.Background.CHANG_BG_COLOR_TYPE)
        val isInBGExceptList = packageName in prefs.get(Preferences.AppList.BG_EXCEPT_LIST)
        val ignoreDarkMode = prefs.get(Preferences.Background.IGNORE_DARK_MODE) || !isHyperOS
        val individualBgColorAppMap = getMapPrefs(
            if (!isDarkMode) Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP
            else Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK
        )
        val individualColor = individualBgColorAppMap[packageName]

        // mTmpAttrs 由 getBGColorFromCache 的 after hook 填充, 但该成员在部分 ROM 上解析不到
        // (hook 根本没装), ColorOS 等分支也可能走不到那条路径, 且 resetCache() 会把它清回 null。
        // 原先这里直接 !!, 一旦为空就会把 NPE 抛回宿主的 build(), 让启动遮罩创建失败
        val tmpAttrs = mTmpAttrsInstance
        val skipAppWithBgColor = bgColorType != 0 &&
                individualColor == null &&
                prefs.get(Preferences.Background.SKIP_APP_WITH_BG_COLOR) &&
                tmpAttrs != null &&
                (ReflectCache.getField<Int>(tmpAttrs, "mWindowBgColor") ?: 0) != 0

        if (skipAppWithBgColor) {
            printLog { "SplashScreenViewBuilder(): skip set bg color cuz app has been set bg color" }
            return null
        }

        return if (individualColor != null) {
            printLog { "SplashScreenViewBuilder(): set individual background color, $individualColor" }
            individualColor.toColorInt()
        } else if (!isInBGExceptList && (!isDarkMode || ignoreDarkMode))
            when (bgColorType) {
                // 从图标取色
                ChangeBGColorTypes.FromIcon.ordinal -> {
                    // currentIconDominantColor 对应的是 currentPackageName 的图标;
                    // 包名不一致 (遮罩流程外的任务级查询) 时取色无意义, 返回 null 不替换
                    if (packageName != currentPackageName) null else {
                        printLog { "SplashScreenViewBuilder(): get adaptive background color" }
                        IconHookHandler.currentIconDominantColor
                            ?: tmpAttrs?.let { ReflectCache.getField<Drawable>(it, "mSplashScreenIcon") }
                            ?.let { drawable ->
                                drawable.drawableDominantColor(
                                    when (bgColorMode) {
                                        BGColorModes.DarkColor.ordinal -> false
                                        BGColorModes.FollowSystem.ordinal -> !isDarkMode
                                        else -> true
                                    }
                                )
                            }
                    }
                }
                // 从壁纸取色
                ChangeBGColorTypes.FromMonet.ordinal -> {
                    printLog { "SplashScreenViewBuilder(): get monet background color" }
                    when (bgColorMode) {
                        BGColorModes.LightColor.ordinal -> monetLightPrimaryContainer(context)
                        BGColorModes.DarkColor.ordinal -> monetDarkSurface(context)
                        else -> if (!isDarkMode)
                            monetLightPrimaryContainer(context)
                        else
                            monetDarkSurface(context)
                    }
                }
                // 自定义颜色
                ChangeBGColorTypes.FromCustom.ordinal -> {
                    printLog { "SplashScreenViewBuilder(): set overall background color" }
                    prefs.get(if (isDarkMode) Preferences.Background.OVERALL_BG_COLOR_NIGHT else Preferences.Background.OVERALL_BG_COLOR)
                        .toColorInt()
                }

                else -> {
                    printLog { "SplashScreenViewBuilder(): not replace background color" }; null
                }
            } else {
            printLog { "SplashScreenViewBuilder(): skip set bg color cuz app in except list" }; null
        }
    }

    /** 系统 Monet 浅色 primaryContainer */
    private fun monetLightPrimaryContainer(ctx: Context): Int =
        ctx.resources.getColor(android.R.color.system_primary_container_light, ctx.theme)

    /** 系统 Monet 深色 surface */
    private fun monetDarkSurface(ctx: Context): Int =
        ctx.resources.getColor(android.R.color.system_surface_dark, ctx.theme)
}
