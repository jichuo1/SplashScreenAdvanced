@file:Suppress("UnstableApiUsage")

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

val projectProperties = Properties().apply {
    file("gradle.properties").inputStream().use { load(it) }
}

fun projectProperty(key: String): String {
    val raw = projectProperties.getProperty(key)
        ?: throw GradleException("Missing property '$key' in app/gradle.properties")
    return Regex("""\$\{([^}]+)}""")
        .replace(raw.trim()) { projectProperty(it.groupValues[1].trim()) }
        .trim()
        .removeSurrounding("\"")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * 读取一项发布签名凭据：Gradle 属性 > 环境变量 > local.properties
 *
 * 凭据只走这三条通道，永远不进仓库。
 */
fun releaseSigningValue(gradleProperty: String, environmentVariable: String): String? =
    providers.gradleProperty(gradleProperty)
        .orElse(providers.environmentVariable(environmentVariable))
        .orNull
        ?.takeIf { it.isNotEmpty() }
        ?: localProperties.getProperty(environmentVariable)?.takeIf { it.isNotEmpty() }

val releaseSigningStoreFile = releaseSigningValue(
    gradleProperty = "splashScreen.signing.storeFile",
    environmentVariable = "SPLASH_SIGNING_STORE_FILE"
)
val releaseSigningStorePassword = releaseSigningValue(
    gradleProperty = "splashScreen.signing.storePassword",
    environmentVariable = "SPLASH_SIGNING_STORE_PASSWORD"
)
val releaseSigningKeyAlias = releaseSigningValue(
    gradleProperty = "splashScreen.signing.keyAlias",
    environmentVariable = "SPLASH_SIGNING_KEY_ALIAS"
)
val releaseSigningKeyPassword = releaseSigningValue(
    gradleProperty = "splashScreen.signing.keyPassword",
    environmentVariable = "SPLASH_SIGNING_KEY_PASSWORD"
)
val releaseSigningValues = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { it != null }
val hasCompleteReleaseSigningValues = releaseSigningValues.all { it != null }

// 只配了一半比完全没配更危险：会让人以为签名生效了
if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigningValues) {
    throw GradleException(
        "Incomplete release signing configuration. Provide storeFile, storePassword, " +
            "keyAlias and keyPassword together, or none of them."
    )
}

/**
 * 发布流水线传入的 versionName 覆盖值
 *
 * gradle.properties 里记录的是稳定版基线（如 1.0.0），Alpha 构建需要打成 1.0.1-alpha.1 这种
 * 带预发布后缀的版本。发布工作流用 -PsplashScreen.releaseVersionName 传入，
 * 并在产物校验阶段比对 APK 里的实际 versionName，确保二者一致。
 */
val releaseVersionNameOverride: String? =
    providers.gradleProperty("splashScreen.releaseVersionName").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = projectProperty("project.app.packageName")
    compileSdk = projectProperty("project.android.compileSdk").toInt()

    defaultConfig {
        applicationId = projectProperty("project.app.packageName")
        minSdk = projectProperty("project.android.minSdk").toInt()
        targetSdk = projectProperty("project.android.targetSdk").toInt()
        versionCode = projectProperty("project.app.versionCode").toInt()
        versionName = releaseVersionNameOverride ?: projectProperty("project.app.versionName")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }

    // 固定发布签名身份。只有四项凭据齐全时才创建，
    // 否则 release 打包会被下方的 taskGraph 守卫拦住，而不是悄悄产出未签名包。
    if (hasCompleteReleaseSigningValues) {
        signingConfigs.create("fixedRelease") {
            storeFile = file(releaseSigningStoreFile!!)
            storePassword = releaseSigningStorePassword
            keyAlias = releaseSigningKeyAlias
            keyPassword = releaseSigningKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            vcsInfo.include = false
            if (hasCompleteReleaseSigningValues) {
                signingConfig = signingConfigs.getByName("fixedRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // 原先的 CI / app 两个 flavor 只服务于旧的 Telegram CI（CI flavor 把 git sha 拼进 versionName）。
    // 现在预发布标识由 Alpha 的版本号本身承载（4.0.1-alpha.1），不需要单独的 flavor，
    // 产物路径也回到 app/build/outputs/apk/{debug,release}/，与发布工作流的校验路径一致。

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

/**
 * 发布打包缺签名身份时硬失败
 *
 * 只圈定真正会产出安装包的任务；minifyReleaseWithR8 这类校验用的 release 任务不在其中，
 * 所以 CI 的 verify 阶段不需要签名密钥也能跑完整门禁。
 */
gradle.taskGraph.whenReady {
    val releasePackagingRequested = allTasks.any { task ->
        task.project == project &&
            (
                task.name.matches(Regex("(?i)^(assemble|bundle|sign).*release.*$")) ||
                    task.name.equals("packageRelease", ignoreCase = true)
            )
    }
    if (releasePackagingRequested && !hasCompleteReleaseSigningValues) {
        throw GradleException(
            "Release packaging requires the fixed signing identity. Configure the " +
                "SPLASH_SIGNING_* environment variables or matching " +
                "splashScreen.signing.* Gradle properties."
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
    }
}

dependencies {
    implementation(projects.hyperxCompose)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.android)
    implementation(libs.kavaref.extension)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // BetterAndroid: 只引入 system-extension。ui-extension 的 toPx/toDp 与 Compose 重名,
    // ui-component 面向 ViewBinding Activity, 都和当前 HyperX Compose 页面不兼容。
    implementation(libs.betterandroid.system.extension)
    implementation(libs.dexkit)
    implementation(libs.androidx.profileinstaller)

    testImplementation("junit:junit:4.13.2")
}

// getGitHeadRefsSuffix 随 CI flavor 一起移除：它唯一的用途是把 git sha 拼进 CI flavor 的
// versionName，而版本号现在由 gradle.properties 与发布工作流共同确定，构建脚本不再读 .git。
