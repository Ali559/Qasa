plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Apply only when Firebase config is present (same graceful degrade as iOS without GoogleService-Info).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Base64 Ed25519 public key used to verify license tokens. Supply it with
// `-PqasaLicensePublicKey=<base64>` or `qasaLicensePublicKey` in gradle.properties; when
// absent the app fetches and caches the key from `/.well-known/qasa-license-key`.
val qasaLicensePublicKey: String = (
    (project.findProperty("qasaLicensePublicKey") as String?) ?: ""
).trim()

// API base URL for debug builds. Defaults to the emulator's host loopback; pass
// `-PqasaApiUrl=http://<your-mac-lan-ip>:8080` to build for a physical device.
val qasaDebugApiUrl: String = (
    (project.findProperty("qasaApiUrl") as String?) ?: "http://10.0.2.2:8080"
).trim()

android {
    namespace = "alibarznji.qasa"
    compileSdk = 35

    defaultConfig {
        applicationId = "alibarznji.qasa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // libqasa_ffi.so (Wi‑Fi sync) is built for these ABIs only; JNA ships more, and a
        // device picking one of those would find no sync library.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        buildConfigField("String", "QASA_API_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "QASA_LICENSE_PUBLIC_KEY", "\"$qasaLicensePublicKey\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "QASA_API_URL", "\"$qasaDebugApiUrl\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "QASA_API_URL", "\"https://api.qasa-app.com\"")
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WireGuard userspace backend (GoBackend) and its VpnService.
    implementation(libs.wireguard.tunnel)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // JNA for the UniFFI bindings (uniffi.qasa_ffi): the aar carries libjnidispatch per ABI.
    implementation(libs.jna) { artifact { type = "aar" } }

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    // 2FA QR codes: Google's code scanner runs in Play services, so no camera permission.
    implementation(libs.play.services.code.scanner)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real SQLite on the JVM: runs the vault DAO's SQL and the Room migration in unit tests.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
