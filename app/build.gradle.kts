plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("dagger.hilt.android.plugin")
    id("com.google.firebase.crashlytics")
    kotlin("kapt")
}

android {
    namespace = "com.kouloundissa.twinstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kouloundissa.twinstracker"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toIntOrNull() ?: 1

        versionName = providers.exec {
            commandLine("git", "describe", "--tags", "--long")
        }.standardOutput.asText.get().trim().let { desc ->
            if (desc.isEmpty()) "1.0.0.0"
            else {
                val parts = desc.split('-', limit = 3)
                val tag = parts.getOrElse(0) { "1.0.0" }.removePrefix("v")
                val commits = parts.getOrElse(1) { "0" }
                "$tag.$commits"
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
                mappingFileUploadEnabled = true
            }
            signingConfig = signingConfigs.getByName("debug")
        }
        create("stage") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
                mappingFileUploadEnabled = true
            }
        }
        named("debug") {
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            versionNameSuffix = "-debug"
            // You can disable collection in debug if desired
            firebaseCrashlytics {
                mappingFileUploadEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

dependencies {

    // === BOMs (Platforms) - Must be declared first ===
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    // === AndroidX - Core ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.i18n)
    implementation(libs.androidx.datastore.preferences)

    // === AndroidX - Activity & Compose ===
    implementation(libs.androidx.activity.compose)

    // === AndroidX - Lifecycle & ViewModel ===
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // === AndroidX - Navigation ===
    implementation(libs.androidx.navigation.compose)

    // === AndroidX - Room (Database) ===
    implementation(libs.androidx.room.ktx)

    // === AndroidX - Compose UI (managed by composeBom) ===
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.test.junit4)

    // === AndroidX - Compose Foundation ===
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)

    // === AndroidX - Compose Material & Icons ===
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // === AndroidX - Compose Animation & Runtime ===
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.saveable)

    // === Dependency Injection (Hilt) ===
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    // === Firebase (managed by firebaseBom) ===
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // === Google Play Services ===
    implementation(libs.play.services.auth)
    implementation(libs.play.services.ads)

    // === External Libraries ===
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.haze)
    implementation(libs.mpandroidchart)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)

    // === Testing ===
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // === Debug ===
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}