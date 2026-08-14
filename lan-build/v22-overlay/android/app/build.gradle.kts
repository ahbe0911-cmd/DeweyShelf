plugins {
    id("com.android.application")
}

android {
    namespace = "ir.local.lantransfer"
    compileSdk = 37

    defaultConfig {
        applicationId = "ir.local.lantransfer"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.2.0"
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
