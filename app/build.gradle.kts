import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "de.wuse.windsurfrechner"
    // Google Play verlangt seit 31.08.2026 für neue Apps Ziel-API 36 (Android 16)
    compileSdk = 36

    defaultConfig {
        applicationId = "de.wuse.windsurfrechner"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.1.$buildNumber"
    }

    // Zwei Varianten:
    //  - github: eigene APK (GitHub-Release), alle Premium-Funktionen frei
    //  - play:   Version für den Play Store, Premium per Einmalkauf über Google Play
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "PREMIUM_FREE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PREMIUM_FREE", "false")
        }
    }

    // Fester Schlüssel im Repo, damit neue APKs sich über alte installieren lassen.
    signingConfigs {
        create("app") {
            storeFile = file("windsurf.keystore")
            storePassword = "windsurf"
            keyAlias = "windsurf"
            keyPassword = "windsurf"
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google Play Billing nur in der Play-Variante
    "playImplementation"("com.android.billingclient:billing:9.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
