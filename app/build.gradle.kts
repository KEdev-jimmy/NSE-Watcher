plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseVersionCode = providers.gradleProperty("NSEWATCHER_VERSION_CODE")
    .orElse(providers.environmentVariable("NSEWATCHER_VERSION_CODE"))
    .orElse("1")
    .get()
    .toIntOrNull()
    ?.takeIf { it in 1..2_100_000_000 }
    ?: error("NSEWATCHER_VERSION_CODE must be a positive supported Android integer.")

val releaseVersionName = providers.gradleProperty("NSEWATCHER_VERSION_NAME")
    .orElse(providers.environmentVariable("NSEWATCHER_VERSION_NAME"))
    .orElse("0.1.0")
    .get()

val releaseKeystore = providers.environmentVariable("NSEWATCHER_RELEASE_KEYSTORE")
    .orNull?.takeIf { it.isNotBlank() }
val releaseStorePassword = providers.environmentVariable("NSEWATCHER_RELEASE_STORE_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
val releaseKeyAlias = providers.environmentVariable("NSEWATCHER_RELEASE_KEY_ALIAS")
    .orNull?.takeIf { it.isNotBlank() }
val releaseKeyPassword = providers.environmentVariable("NSEWATCHER_RELEASE_KEY_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
val signingInputs = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = signingInputs.all { it != null }
check(signingInputs.all { it == null } || releaseSigningConfigured) {
    "Release signing is incomplete. Restore all permanent signing inputs; do not substitute a new key."
}

android { namespace = "ke.co.nsewatcher"; compileSdk = 35
    defaultConfig {
        applicationId = "ke.co.nsewatcher"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("permanentRelease") {
                storeFile = file(requireNotNull(releaseKeystore))
                storeType = "PKCS12"
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("permanentRelease")
            }
        }
    }
    lint {
        disable += "ProduceStateDoesNotAssignValue"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("io.coil-kt.coil3:coil-svg:3.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


