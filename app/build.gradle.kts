plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.extendedreality"
    compileSdk = 37
    aaptOptions {
        noCompress("tflite")
    }

    defaultConfig {
        applicationId = "com.example.extendedreality"
        minSdk = 24
        targetSdk = 37
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
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // CameraX (For the live camera feed)
    val camerax_version = "1.4.0-alpha04"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // Google ML Kit (For Image Labeling and Text Recognition)
    implementation("com.google.mlkit:image-labeling:17.0.8")
    implementation("com.google.mlkit:object-detection-custom:17.0.2")
    implementation("com.google.mlkit:linkfirebase:17.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

}