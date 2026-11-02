import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.SplashScreenAdvanced.xposedmodule.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":app"

    // Macrobenchmark force-stops and relaunches the target: the instrumentation must live in its own process, not the app's.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
