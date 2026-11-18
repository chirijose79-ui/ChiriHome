plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Datos de la llave de firma. Viven en ~/.gradle/gradle.properties, fuera del proyecto y del repo
val llaveArchivo: String? = providers.gradleProperty("CHIRI_STORE_FILE").orNull
val llaveClaveAlmacen: String? = providers.gradleProperty("CHIRI_STORE_PASSWORD").orNull
val llaveAlias: String? = providers.gradleProperty("CHIRI_KEY_ALIAS").orNull
val llaveClave: String? = providers.gradleProperty("CHIRI_KEY_PASSWORD").orNull
val firmaCompleta = llaveArchivo != null &&
        llaveClaveAlmacen != null &&
        llaveAlias != null &&
        llaveClave != null

android {
    namespace = "com.chirihome.music"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chirihome.music"
        minSdk = 26
        targetSdk = 37
        // Cada versión que repartas debe tener un versionCode mayor que la anterior
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (firmaCompleta) {
                storeFile = file(llaveArchivo!!)
                storePassword = llaveClaveAlmacen
                keyAlias = llaveAlias
                keyPassword = llaveClave
            }
        }
    }

    buildTypes {
        release {
            // Sin los datos de firma, el APK sale como "app-release-unsigned.apk"
            if (firmaCompleta) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Red: API Subsonic de Navidrome (JSON)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Reproducción de audio y video
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)

    // Carátulas
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}