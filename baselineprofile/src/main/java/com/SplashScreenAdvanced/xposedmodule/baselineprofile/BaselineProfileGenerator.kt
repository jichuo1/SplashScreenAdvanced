package com.SplashScreenAdvanced.xposedmodule.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在已连接设备上采集设置页启动路径，输出可覆盖 `app/src/main/baseline-prof.txt`。
 *
 * `./gradlew :baselineprofile:connectedDebugAndroidTest`（模拟器 / debuggable 目标需追加
 * `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,DEBUGGABLE`），
 * 产物 `BaselineProfileGenerator_generate-startup-prof.txt` 在 `build/outputs/connected_android_test_additional_output` 下。
 *
 * 不使用 `androidx.baselineprofile` Gradle 插件：1.4.x 不支持 AGP 9，1.5 仍是 alpha。
 * 安装期生效靠 AGP 打包的 `baseline-prof.txt` + `profileinstaller`。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // The dock pages that are not on screen are built a moment after launch (idle frames); wait for that,
            // then page through them so the swipe, the text chain and the page transitions are recorded too.
            device.waitForIdle()
            Thread.sleep(PREWARM_WAIT_MS)
            val width = device.displayWidth
            val middle = device.displayHeight / 2
            repeat(TAB_SWIPES) {
                device.swipe((width * 0.85f).toInt(), middle, (width * 0.15f).toInt(), middle, SWIPE_STEPS)
                device.waitForIdle()
            }
            repeat(TAB_SWIPES) {
                device.swipe((width * 0.15f).toInt(), middle, (width * 0.85f).toInt(), middle, SWIPE_STEPS)
                device.waitForIdle()
            }
            device.swipe(width / 2, (device.displayHeight * 0.8f).toInt(), width / 2, (device.displayHeight * 0.3f).toInt(), SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.SplashScreenAdvanced.xposedmodule"
        const val PREWARM_WAIT_MS = 2000L
        const val TAB_SWIPES = 4
        const val SWIPE_STEPS = 12
    }
}
