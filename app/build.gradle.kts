import java.text.SimpleDateFormat
import java.util.Date
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("dagger.hilt.android.plugin")
    kotlin("kapt")
    id("kotlin-kapt")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.kouloundissa.twinstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kouloundissa.twinstracker"
        minSdk = 31
        targetSdk = 36
        versionCode = getGitCommitCount()
        versionName = getGitVersionName()

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
            isMinifyEnabled    = true
            isShrinkResources  = true
            isDebuggable       = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel          = "FULL"
            firebaseCrashlytics {
                nativeSymbolUploadEnabled  = true
                mappingFileUploadEnabled   = true
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.ui.text)

    //icons
    implementation(libs.material.icons.core) // Or the latest version
    implementation(libs.material.icons.extended) // For a wider selection, including all themes

    // VieModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.appcheck)// App Check dependency
    implementation(libs.firebase.appcheck.playintegrity)   // Play Integrity provider (recommended for production)
    implementation(libs.firebase.appcheck.debug)// Debug provider (for development/testing)
    implementation(libs.firebase.crashlytics.ktx)   // Firebase Crashlytics

    // Graphics
    implementation(libs.mpandroidchart)

    // Dates
    implementation(libs.joda.time)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.google.firebase.crashlytics.ktx)
    implementation(libs.room.ktx)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    kapt(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.reflect)

    implementation(libs.haze)

    implementation(libs.coil.compose)
    // auth
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

}

// --- Fonctions Git Versioning ---

/**
 * Retourne le nombre total de commits Git (versionCode).
 * Renvoie 1 en cas d’erreur.
 */
fun getGitCommitCount(): Int = runCatching {
    ByteArrayOutputStream().use { stdout ->
        exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = stdout
        }
        stdout.toString().trim().toInt()
    }
}.getOrDefault(1)

/**
 * Retourne la versionName basée sur le dernier tag Git et le nombre de commits depuis ce tag, sous la forme "X.Y.Z.N".
 * Renvoie "1.0.0.0" en cas d’erreur.
 */
fun getGitVersionName(): String = runCatching {
    ByteArrayOutputStream().use { stdout ->
        exec {
            commandLine("git", "describe", "--tags", "--long")
            standardOutput = stdout
        }
        val desc = stdout.toString().trim()              // ex: v1.2.0-5-g1234567
        val (rawTag, commits) = desc.split('-', limit = 3).let {
            it[0] to it.getOrElse(1) { "0" }
        }
        val tag = rawTag.removePrefix("v")
        "$tag.$commits"
    }
}.getOrDefault("1.0.0.0")