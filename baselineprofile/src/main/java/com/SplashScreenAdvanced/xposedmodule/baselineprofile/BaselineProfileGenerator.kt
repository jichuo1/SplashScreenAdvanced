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
 * `./gradlew :baselineprofile:connectedDebugAndroidTest`
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
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.SplashScreenAdvanced.xposedmodule"
    }
}
