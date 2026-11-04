plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub numbers every build; that number becomes the version (1.0.1, 1.0.2, …).
// The +100 keeps it above the 0.x versions installed from Android Studio.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

android {
    namespace = "com.hmj.twotone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hmj.twotone"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // If you copy your computer's Android Studio key in as signing/debug.keystore,
            // GitHub builds install straight over the version you have now (nothing is lost).
            // Otherwise the key made for this project is used.
            val studioKey = rootProject.file("signing/debug.keystore")
            if (studioKey.exists()) {
                storeFile = studioKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                storeFile = rootProject.file("signing/twotone.keystore")
                storePassword = "twotone"
                keyAlias = "twotone"
                keyPassword = "twotone"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Lint warnings shouldn't stop a build from GitHub.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    // Reads web pages for the news section (open source, MIT licence)
    implementation("org.jsoup:jsoup:1.17.2")
}
