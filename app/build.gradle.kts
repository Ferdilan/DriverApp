import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle.plugin)
}

// 1. Baca local.properties SEKALI saja di sini untuk semua keperluan
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.example.driverapp"
    compileSdk = 36 // Gunakan sintaks sederhana ini

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.driverapp"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2. Masukkan API Key Maps ke Manifest
        // Pastikan di local.properties ada: MAPS_API_KEY=AIzaSy...
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")

        // 3. Masukkan Konfigurasi MQTT ke BuildConfig (Code Java)
        // Pastikan di local.properties ada: MQTT_HOST, MQTT_USERNAME, MQTT_PASSWORD
        buildConfigField("String", "MQTT_HOST", "\"${localProperties.getProperty("MQTT_HOST", "")}\"")
        buildConfigField("String", "MQTT_USERNAME", "\"${localProperties.getProperty("MQTT_USERNAME", "")}\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"${localProperties.getProperty("MQTT_PASSWORD", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Paho MQTT
    implementation("com.amazon.ion:ion-java:1.10.4")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Google Maps & Location
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.maps.android:android-maps-utils:3.4.0")

    // Android Standard Libs
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}