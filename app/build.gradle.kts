import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreB64 = file("horus.keystore.b64")
val keystoreFile = file("build/horus.keystore")
if (keystoreB64.exists()) {
    keystoreFile.parentFile.mkdirs()
    keystoreFile.writeBytes(Base64.getMimeDecoder().decode(keystoreB64.readText()))
}

android {
    namespace = "com.horusai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.horusai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.1-alpha"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = keystoreFile
            storePassword = "horus-ai"
            keyAlias = "horus"
            keyPassword = "horus-ai"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
