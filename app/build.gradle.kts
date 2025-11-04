plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.sriox.vasatey"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sriox.vasatey"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    aaptOptions {
        noCompress("pv", "ppn")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- Android Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // --- Firebase (only for Messaging) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-messaging-ktx")

    // --- Supabase ---
    val supabaseVersion = "2.2.1"
    implementation(platform("io.github.jan-supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-supabase:gotrue-kt")
    implementation("io.github.jan-supabase:postgrest-kt")
    implementation("io.github.jan-supabase:realtime-kt")
    implementation("io.github.jan-supabase:storage-kt")
    implementation("io.github.jan-supabase:functions-kt")

    // --- Ktor (Required by Supabase) ---
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")

    // --- Picovoice (Wake Word Engine) ---
    implementation("ai.picovoice:porcupine-android:3.0.2")

    // --- Lifecycle + Coroutines ---
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Networking ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // --- Location & Maps ---
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
}
