import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.dumpkin"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dumpkin"
        minSdk = 26
        // Deliberately 28: Android 10+ blocks exec() of files in an app's data directory for apps
        // targeting API 29+. The Pumpkin engine is downloaded at runtime and executed from there
        // (same approach Termux uses), so this app is meant to be sideloaded, not published on Play.
        targetSdk = 28
        versionCode = 4
        versionName = "0.4.0-alpha"
    }

    // Release signing. The key lives outside the repo; see "Release signing" in the README.
    // Settings come from keystore.properties (git-ignored) or DUMPKIN_* environment variables.
    val signingProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        signingProps.getProperty(prop)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    val releaseStore = signingValue("storeFile", "DUMPKIN_KEYSTORE_FILE")?.let { file(it) }
    val hasReleaseKey = releaseStore?.exists() == true

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStore
                storePassword = signingValue("storePassword", "DUMPKIN_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DUMPKIN_KEY_ALIAS") ?: "dumpkin"
                keyPassword = signingValue("keyPassword", "DUMPKIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // Contributors without the key can still build an installable APK, but it must never be published.
                // Pass -PrequireReleaseKey (e.g. in a release script) to turn this into a hard error.
                if (project.hasProperty("requireReleaseKey")) {
                    throw GradleException("Release signing key not found. Create keystore.properties (see README).")
                }
                logger.warn("WARNING: no release signing key found, signing the release build with the DEBUG key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // targetSdk 28 is intentional (see above).
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.coroutines.android)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
