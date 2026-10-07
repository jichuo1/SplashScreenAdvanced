package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import android.graphics.drawable.Drawable
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.wrapper.splashBackgroundWithPreview

/**
 * 此对象用于处理针对 Oplus 的 Hook
 */
object OplusHookHandler : BaseHookHandler() {

    /** 开始 Hook */
    override fun onHook() {
        // ColorOS fills/updates the background on its executor after makeSplashScreenContentView returns.
        // Follow the exact splash View rather than the finished ThreadLocal session or a latest package name.
        SystemUIHooker.Members.setContentViewBackground_OplusShellStartingWindowManager.addBeforeHook({ true }) {
            val background = BgHookHandler.backgroundFor(args.getOrNull(0)) ?: return@addBeforeHook
            if (background.customApplied) {
                printLog { "ColorOS: keep applied custom splash background" }
                resultNull()
            } else {
                val preview = args.getOrNull(1) as? Drawable ?: return@addBeforeHook
                args(1).set(splashBackgroundWithPreview(background.color, preview))
                printLog { "ColorOS: keep opaque splash color behind system preview" }
            }
        }

        // 处理 Drawable 图标
        SystemUIHooker.Members.getIconExt_OplusShellStartingWindowManager.addAfterHook {
            printLog { "ColorOS: getIconExt_OplusShellStartingWindowManager(): current method is getIconExt" }
            result = IconHookHandler.processIconDrawable(result as Drawable)
        }

        // 禁止读取 WindowAttrs 缓存
        SystemUIHooker.Members.getWindowAttrsIfPresent_OplusShellStartingWindowManager.addBeforeHook {
            printLog { "ColorOS: getWindowAttrsIfPresent_OplusShellStartingWindowManager(): return false" }
            resultFalse()
        }
    }
}
