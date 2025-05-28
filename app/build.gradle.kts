plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ishade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ishade"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes.add("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.org.eclipse.paho.android.service) // Servicio android

    implementation(libs.localbroadcastmanager)
    implementation("com.android.support:localbroadcastmanager:28.0.0")

    implementation("com.google.code.gson:gson:2.13.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0") // o la última versión estable
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.0") // o la última versión estable
    implementation("androidx.fragment:fragment-ktx:1.8.7") // para activityViewModels

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}