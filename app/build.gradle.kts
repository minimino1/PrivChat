plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Release-Signierung kommt aus Gradle-Properties oder Umgebungsvariablen (CI Secrets).
// Ohne diese Werte wird nur das Debug-APK gebaut.
val keystoreFile = providers.gradleProperty("KEYSTORE_FILE")
    .orElse(providers.environmentVariable("KEYSTORE_FILE")).orNull
val keystorePassword = providers.gradleProperty("KEYSTORE_PASSWORD")
    .orElse(providers.environmentVariable("KEYSTORE_PASSWORD")).orNull
val keyAlias = providers.gradleProperty("KEY_ALIAS")
    .orElse(providers.environmentVariable("KEY_ALIAS")).orNull
val keyPassword = providers.gradleProperty("KEY_PASSWORD")
    .orElse(providers.environmentVariable("KEY_PASSWORD")).orNull
val hasReleaseSigning = listOf(keystoreFile, keystorePassword, keyAlias, keyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "dev.and.chatt"
    compileSdk = 36
    // buildToolsVersion bewusst weggelassen -> AGP 9.4 nimmt 36.0.0 als Default.

    defaultConfig {
        applicationId = "dev.and.chatt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = keyAlias
                keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Kein applicationIdSuffix: google-services.json matcht auf den Paketnamen.
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
}