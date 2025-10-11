package com.gswxxn.restoresplashscreen.hook.systemui

import android.graphics.drawable.Drawable
import com.gswxxn.restoresplashscreen.data.StartingWindowInfo
import com.gswxxn.restoresplashscreen.hook.SystemUIHooker
import com.gswxxn.restoresplashscreen.hook.base.BaseHookHandler
import com.gswxxn.restoresplashscreen.utils.YukiHelper.getField
import com.gswxxn.restoresplashscreen.utils.YukiHelper.printLog
import com.gswxxn.restoresplashscreen.utils.YukiHelper.setField

/**
 * 此对象用于处理针对 ColorOS 的 Hook
 */
object ColorOSHookHandler : BaseHookHandler() {

    /** 开始 Hook */
    override fun onHook() {
        SystemUIHooker.Members.setContentViewBackground_OplusShellStartingWindowManager.addBeforeHook {
            printLog("ColorOS: setContentViewBackground_OplusShellStartingWindowManager(): intercept!!")
            resultNull()
        }

        // 处理 Drawable 图标
        SystemUIHooker.Members.getIconExt_OplusShellStartingWindowManager.addAfterHook {
            printLog("ColorOS: getIconExt_OplusShellStartingWindowManager(): current method is getIconExt")
            result = IconHookHandler.processIconDrawable(result as Drawable)
        }

        // 禁止读取 WindowAttrs 缓存
        SystemUIHooker.Members.getWindowAttrsIfPresent_OplusShellStartingWindowManager.addBeforeHook {
            printLog("ColorOS: getWindowAttrsIfPresent_OplusShellStartingWindowManager(): return false")
            resultFalse()
        }
    }
}