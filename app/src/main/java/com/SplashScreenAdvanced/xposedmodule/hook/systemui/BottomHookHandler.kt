package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.wrapper.SplashScreenViewBuilderWrapper

/**
 * 此对象用于处理底部图片 Hook
 */
object BottomHookHandler : BaseHookHandler() {

    /** 开始 Hook */
    override fun onHook() {
        /**
         * 移除底部图片
         */
        SystemUIHooker.Members.build_SplashScreenViewBuilder.addBeforeHook {
            val isRemoveBrandingImage = prefs.get(Preferences.Display.REMOVE_BRANDING_IMAGE) &&
                    if (prefs.get(Preferences.Scope.IS_REMOVE_BRANDING_IMAGE_EXCEPTION_MODE))
                        GenerateHookHandler.currentPackageName !in prefs.get(Preferences.AppList.REMOVE_BRANDING_IMAGE_LIST)
                    else
                        GenerateHookHandler.currentPackageName in prefs.get(Preferences.AppList.REMOVE_BRANDING_IMAGE_LIST)

            if (isRemoveBrandingImage)
                SplashScreenViewBuilderWrapper.getInstance(instance!!).setBrandingDrawable(null, 0, 0)
            printLog { "SplashScreenViewBuilder():${if (isRemoveBrandingImage) "" else " not"} remove branding image" }
        }
    }
}
