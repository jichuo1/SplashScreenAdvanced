package com.SplashScreenAdvanced.xposedmodule.utils

import com.highcapable.kavaref.extension.toClass

/**
 * 进程内的设备判断
 *
 * 这里刻意不使用 BetterAndroid 的 `RomType`。
 *
 * `RomType.HYPEROS` 依赖 `ro.mi.os.version.*` 或 (`android.miui.R` + MIUI V816),
 * `RomType.MIUI` 会排除 HyperOS; 而本模块的 [isHyperOS] 只看 `android.miui.R` 是否存在,
 * 实际含义是 "MIUI 系 (含旧 MIUI 与 HyperOS)"。调用点用它决定是否走小米启动遮罩逻辑,
 * 换成 `RomType` 会在旧 MIUI / 部分刷机设备上改掉行为。
 *
 * [isColorOS] 同样不能换成 `RomType.COLOROS`: 后者会排除 realmeUI, 本模块需要把带
 * `oplus.R` / `oppo.R` 的设备都走 ColorOS 分支。
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

    /**
     * 是否为 AfterlifeOS
     *
     * 该 ROM 把 AOSP 主线的 splash 改动 backport 进了 Android 14 分支, 是启动遮罩 chooseStyle
     * 适配需要覆盖的典型对象。判据来自其 vendor_afterlife/config/version.mk 导出的 ro.afterlife.*。
     *
     * 当前适配**不**按 ROM 分支 (任何 ROM 上都只在「强制开启启动遮罩」开启时才改写参数, 见
     * GenerateHookHandler 中 chooseStyle 处的说明), 这里保留一个可判定的 ROM 标识, 供后续需要
     * 差异化处理时直接使用, 而非在当前流程里做无谓分支。
     */
    val isAfterlifeOS: Boolean by lazy {
        runCatching {
            val systemProperties = "android.os.SystemProperties".toClass()
            val get = systemProperties.getMethod("get", String::class.java)
            !(get.invoke(null, "ro.afterlife.version") as? String).isNullOrEmpty()
        }.getOrDefault(false)
    }
}
