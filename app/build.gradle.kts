import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCompose)
}

/**
 * CI's run number, so every APK Actions publishes is newer than the one before
 * it and a phone treats installing it as an update rather than a reinstall. It
 * is also the only way to tell, from the phone, which build is on it.
 *
 * 1 everywhere else: outside CI nothing is being updated over the air.
 */
val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

android {
    namespace = "com.etoken"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.etoken"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // A debug keystore of the project's own, committed alongside the code.
    //
    // Without this, `debug` is signed with AGP's auto-generated
    // ~/.android/debug.keystore -- and a GitHub runner has no such file, so the
    // build creates a fresh one with a new key on every single run. Android
    // refuses to update an installed app whose signature does not match, so
    // each APK from CI could only be installed by uninstalling the last one.
    // That is what "conflicto con el paquete" on the phone means.
    //
    // A debug keystore is not a secret and is not treated as one: these are the
    // credentials Android itself ships for debug builds, publicly documented.
    // What it buys is that every machine -- CI, and anyone's checkout -- signs
    // with the same key. The release build has no signing config and is not
    // meant to inherit this one (C4).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        // `debug` picks up signingConfigs["debug"] on its own; the block above
        // is the whole of it.
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions {
        unitTests { isReturnDefaultValues = true }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // The splash screen, on every version. Android 12 draws one whether the
    // app asks or not; this backports the same one to 8 through 11 and is
    // what lets MainActivity hold it while the store is read.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // collectAsStateWithLifecycle()
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Debug builds only, wired from AppContainer: see Network.moxfieldApi(logRequests).
    implementation(libs.okhttp.logging.interceptor)

    // Coil 3: commander art in the deck grid, token art in the token grid.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented Compose tests. They drive the real screens against fake
    // APIs, which is the closest thing to watching the app work that does not
    // need a person holding a phone.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
