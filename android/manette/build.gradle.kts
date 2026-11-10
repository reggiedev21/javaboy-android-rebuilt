// Enveloppe « bibliothèque » autour du dépôt Manette (submodule third_party/Manette).
//
// Le dépôt du binôme est une application ; ce module en compile les sources telles quelles
// avec le plugin bibliothèque, sans rien y modifier. Seul le manifeste est remplacé
// (src/main/AndroidManifest.xml) : pas d'icône de lancement, pas d'attributs <application>.
//
//   ./gradlew :manette:assembleRelease   ->  manette/build/outputs/aar/manette-release.aar
plugins { id("com.android.library") }

val manetteSrc = rootDir.parentFile.resolve("third_party/Manette/app/src/main")

android {
    // Doit rester le package de la classe R utilisée par les sources du binôme.
    namespace = "com.example.manette"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Le dépôt Manette déclare minSdk 30 ; abaissé à celui de :app pour la fusion des manifestes.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir(manetteSrc.resolve("java"))
            res.srcDir(manetteSrc.resolve("res"))
            // Les assets (ROMs de la banque de jeux) ne font pas partie de la bibliothèque :
            // :app les ajoute dans sa seule variante « demo ».
        }
    }
}

dependencies {
    // Mêmes dépendances que third_party/Manette/app/build.gradle.kts.
    implementation(manetteLibs.activity.ktx)
    implementation(manetteLibs.appcompat)
    implementation(manetteLibs.constraintlayout)
    implementation(manetteLibs.material)
    implementation("com.google.code.gson:gson:2.10.1")
}
