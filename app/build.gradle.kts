import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release key lives in the (private) repo so every build, local or CI, is
// signed identically and can update the installed app in place.
val signingProps = Properties().apply {
    rootProject.file("signing/keystore.properties").inputStream().use(::load)
}

// CI passes VERSION_CODE (the run number, shared by both channels), VERSION_NAME and CHANNEL.
// Local builds fall back to the commit count and version.txt.
val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

android {
    namespace = "com.pagnchanak.systemtools"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pagnchanak.systemtools"
        minSdk = 29
        targetSdk = 37
        versionCode = providers.environmentVariable("VERSION_CODE").map(String::toInt).orNull ?: gitCommitCount.get()
        versionName = providers.environmentVariable("VERSION_NAME").orNull
            ?: rootProject.file("version.txt").readText().trim()
        // Release channel this build belongs to; the updater follows it by default.
        buildConfigField("String", "CHANNEL", "\"${providers.environmentVariable("CHANNEL").orNull ?: "dev"}\"")
        buildConfigField("String", "GITHUB_REPO", "\"pagnchanak/android-system-tools\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/${signingProps.getProperty("storeFile")}")
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Same key as release so debug and release builds can replace each other.
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.libsu.core)
}
