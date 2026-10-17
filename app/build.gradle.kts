import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.richizu.fixrkn"
    compileSdk = 36
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "com.richizu.fixrkn"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    // One permanent key for every build (local and CI), so each new APK installs
    // over the previous one as an update. Override the store with RKN_KEYSTORE /
    // RKN_KEYSTORE_PASSWORD / RKN_KEY_ALIAS / RKN_KEY_PASSWORD to use your own key.
    signingConfigs {
        create("app") {
            storeFile = file(System.getenv("RKN_KEYSTORE") ?: "keystore/release.jks")
            storePassword = System.getenv("RKN_KEYSTORE_PASSWORD") ?: "richizufixrkn"
            keyAlias = System.getenv("RKN_KEY_ALIAS") ?: "richizufixrkn"
            keyPassword = System.getenv("RKN_KEY_PASSWORD") ?: "richizufixrkn"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Optional host build of libbyedpi for ByeDpiHostTest (see the test for details).
            System.getenv("RKN_HOST_LIBS")?.let { test.systemProperty("java.library.path", it) }
            System.getenv("RKN_NET_TEST")?.let { test.environment("RKN_NET_TEST", it) }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

base {
    archivesName.set("RichizuFixRKN-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// hev-socks5-tunnel is built with ndk-build (its upstream build system).
// ---------------------------------------------------------------------------

val sdkDir: File = run {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { local.load(it) }
    val path = local.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found: set sdk.dir in local.properties or ANDROID_HOME")
    File(path)
}
val ndkDir = File(sdkDir, "ndk/${android.ndkVersion}")
val tunDir = file("src/main/cpp/tun")
val hevDir = file("src/main/cpp/hev-socks5-tunnel")

val patchTun = tasks.register("patchTun") {
    val patch = File(tunDir, "hev-drop-quic.patch")
    inputs.file(patch)
    doLast {
        fun git(vararg args: String) = providers.exec {
            workingDir = hevDir
            commandLine("git", *args)
            isIgnoreExitValue = true
        }.result.get().exitValue
        val alreadyApplied = git("apply", "--reverse", "--check", patch.absolutePath) == 0
        if (!alreadyApplied && git("apply", patch.absolutePath) != 0) {
            throw GradleException("Failed to apply ${patch.name}")
        }
    }
}

val buildTun = tasks.register<Exec>("buildTun") {
    dependsOn(patchTun)
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    executable = File(ndkDir, if (isWindows) "ndk-build.cmd" else "ndk-build").absolutePath
    args(
        "NDK_PROJECT_PATH=${layout.buildDirectory.get().asFile}/ndk-tun",
        "NDK_LIBS_OUT=${file("src/main/jniLibs")}",
        "APP_BUILD_SCRIPT=${File(tunDir, "Android.mk")}",
        "NDK_APPLICATION_MK=${File(tunDir, "Application.mk")}",
        "-j${Runtime.getRuntime().availableProcessors()}"
    )
}

tasks.named("preBuild") { dependsOn(buildTun) }
