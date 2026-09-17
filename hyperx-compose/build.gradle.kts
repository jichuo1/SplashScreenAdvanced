import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
    }
}

android {
    namespace = "dev.lackluster.hyperx"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    api(libs.miuix.ui)
    api(libs.miuix.preference)
    api(libs.miuix.icons)
    api(libs.miuix.blur)
    api(libs.miuix.squircle)
    api(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.ripple)
    api(libs.androidx.activity.compose)
    api(libs.miuix.navigation3.ui)
    api(libs.androidx.navigation3.runtime)
    api(libs.jetbrains.navigationevent.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation("junit:junit:4.13.2")
}