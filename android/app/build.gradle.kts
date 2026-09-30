plugins { id("com.android.application") }

android {
    namespace = "javaboy.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "javaboy.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.92-android"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.6")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference:1.2.1")
}
