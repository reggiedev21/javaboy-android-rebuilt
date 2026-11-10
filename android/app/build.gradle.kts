plugins { id("com.android.application") }

android {
    namespace = "javaboy.android"
    // 37 : imposé par les dépendances AndroidX du module :manette.
    compileSdk {
        version = release(37)
    }

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

    // demo : banque de jeux de Manette avec ses ROMs embarquées. Jamais publiée.
    // play : version Play Store, aucune ROM embarquée ; la banque est remplacée par la
    //        bibliothèque de l'émulateur (voir res/values/flavor.xml et ShellBinder).
    flavorDimensions += "distribution"
    productFlavors {
        create("demo") {
            dimension = "distribution"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("play") {
            dimension = "distribution"
        }
    }

    sourceSets {
        getByName("demo") {
            assets.srcDir(rootDir.parentFile.resolve("third_party/Manette/app/src/main/assets"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":manette"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.6")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference:1.2.1")
}
