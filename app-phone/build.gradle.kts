import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.sync.alarm"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sync.alarm"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keyStorePath = localProperties.getProperty("KEYSTORE_FILE")
                ?: (findProperty("KEYSTORE_FILE") as String?)
                ?: System.getenv("KEYSTORE_FILE")
            if (!keyStorePath.isNullOrBlank()) {
                val keyFile = rootProject.file(keyStorePath)
                if (keyFile.exists()) {
                    storeFile = keyFile
                }
            }
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                ?: (findProperty("KEYSTORE_PASSWORD") as String?)
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: ""
            keyAlias = localProperties.getProperty("KEY_ALIAS")
                ?: (findProperty("KEY_ALIAS") as String?)
                ?: System.getenv("KEY_ALIAS")
                ?: ""
            keyPassword = localProperties.getProperty("KEY_PASSWORD")
                ?: (findProperty("KEY_PASSWORD") as String?)
                ?: System.getenv("KEY_PASSWORD")
                ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        // Targeted suppression for pure-Compose activity (no FragmentActivity)
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    implementation(project(":core"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (Phone)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
