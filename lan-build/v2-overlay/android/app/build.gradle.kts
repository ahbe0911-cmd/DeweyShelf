plugins {
    id("com.android.application")
}

android {
    namespace = "ir.local.lantransfer"
    compileSdk = 37

    defaultConfig {
        applicationId = "ir.local.lantransfer"
        minSdk = 26
        // target 36 deliberately keeps LAN access under INTERNET permission.
        // If you raise targetSdk to 37, also request ACCESS_LOCAL_NETWORK at runtime.
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
}
