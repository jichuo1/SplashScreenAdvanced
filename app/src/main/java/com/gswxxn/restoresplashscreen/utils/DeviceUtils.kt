package com.gswxxn.restoresplashscreen.utils

import com.highcapable.kavaref.extension.toClass

/**
 * 进程内的设备判断
 */
object DeviceUtils {
    /**
     * 是否为 MIUI 系系统 (含 HyperOS)
     *
     * 注意名字与判据并不严格对应: `android.miui.R` 在 MIUI 12/13/14 上同样存在, 所以这里实际是
     * "MIUI 系"而非"HyperOS"。保留原名以免大面积改动调用点, 但依赖它做版本区分时要意识到这一点——
     * 如果将来需要区分 HyperOS 与旧 MIUI, 必须另找判据 (如 ro.mi.os.version.incremental)
     */
    val isHyperOS: Boolean by lazy { runCatching { "android.miui.R".toClass() }.isSuccess }

    val isColorOS: Boolean by lazy {
        runCatching { ("oppo.R").toClass() }.isSuccess ||
                runCatching { "com.color.os.ColorBuild".toClass() }.isSuccess ||
                runCatching { "oplus.R".toClass() }.isSuccess
    }
}
