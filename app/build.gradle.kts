import java.net.URL
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.shadiao.nb"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shadiao.nb"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "5.3.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  AndroidLibXrayLite — 获取 libv2ray.aar 的三级回退策略
// ════════════════════════════════════════════════════════════════════════
//
//  优先级 1 — 从 Go 源码编译（需要 Go + gomobile + NDK）
//  优先级 2 — 使用本地预编译 app/libs/libv2ray.aar
//  优先级 3 — 从 GitHub Releases 自动下载
//
//  强制跳过源码编译：./gradlew build -PskipXrayBuild
//
val xraySrcDir = rootProject.file("AndroidLibXrayLite")
val aarFile = file("libs/libv2ray.aar")
val aarDownloadUrl = "https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar"

/**
 * 检查命令是否可用（在 PATH 中）
 */
fun isCommandAvailable(cmd: String): Boolean = try {
    val pb = ProcessBuilder(if (System.getProperty("os.name").lowercase().contains("windows")) "where" else "which", cmd)
    pb.redirectErrorStream(true)
    pb.start().waitFor() == 0
} catch (_: Exception) { false }

// ── 任务 1：从 Go 源码编译 ──
//  仅当 go + gomobile 都可用且未跳过时才执行
//  设置 ignoreExitValue = true，编译失败不会中断构建
val buildXrayAar = tasks.register<Exec>("buildXrayAar") {
    group = "xray"
    description = "从 Go 源码编译 AndroidLibXrayLite → libv2ray.aar"

    workingDir = xraySrcDir
    val gomobileCmd = if (System.getProperty("os.name").lowercase().contains("windows")) {
        "gomobile.exe"
    } else {
        "gomobile"
    }

    commandLine(
        gomobileCmd, "bind",
        "-v",
        "-androidapi", "24",
        "-trimpath",
        "-ldflags=-s -w -buildid= -checklinkname=0",
        "./"
    )

    // 编译失败不中断构建（回退到下载）
    isIgnoreExitValue = true

    doLast {
        val generated = File("$xraySrcDir/libv2ray.aar")
        if (generated.exists() && generated.length() > 0) {
            aarFile.parentFile.mkdirs()
            generated.copyTo(aarFile, overwrite = true)
            generated.delete()
            logger.lifecycle("✓ libv2ray.aar 已从 Go 源码编译完成")
        }
    }

    onlyIf {
        val skip = project.hasProperty("skipXrayBuild")
        if (skip) {
            logger.lifecycle("⚠ skipXrayBuild 已设置，跳过 Go 源码编译")
            return@onlyIf false
        }
        val goOk = isCommandAvailable("go")
        val gomobileOk = isCommandAvailable(gomobileCmd)
        if (!goOk || !gomobileOk) {
            logger.lifecycle("⚠ Go=${goOk}, gomobile=${gomobileOk} — 跳过源码编译，将尝试下载")
            return@onlyIf false
        }
        true
    }
}

// ── 任务 2：从 GitHub 下载预编译 .aar ──
//  在 buildXrayAar 之后执行，仅在 .aar 不存在时下载
val downloadXrayAar = tasks.register("downloadXrayAar") {
    group = "xray"
    description = "从 GitHub Releases 下载 libv2ray.aar"

    // 确保在源码编译任务之后执行（编译成功就不需要下载）
    dependsOn(buildXrayAar)

    onlyIf {
        !aarFile.exists() || aarFile.length() == 0L
    }

    doLast {
        aarFile.parentFile.mkdirs()
        logger.lifecycle("⬇ 从 GitHub 下载 libv2ray.aar...")
        val connection = URL(aarDownloadUrl).openConnection()
        connection.connectTimeout = 60000
        connection.readTimeout = 300000
        connection.setRequestProperty("User-Agent", "Gradle")
        connection.getInputStream().use { input ->
            aarFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("✓ libv2ray.aar 下载完成 (${aarFile.length() / 1024 / 1024}MB)")
    }
}

// preBuild 之前：源码编译 → 若无 .aar 则自动下载
tasks.named("preBuild") {
    dependsOn(downloadXrayAar)
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Xray core (AndroidLibXrayLite)
    // 构建时自动获取：源码编译 > 本地 .aar > GitHub 下载
    implementation(files("libs/libv2ray.aar"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
