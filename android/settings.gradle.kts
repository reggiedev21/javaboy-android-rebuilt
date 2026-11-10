// Projet Android Studio : ouvrir CE dossier. Le module :core est celui du dépôt (../core).
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Dépôt du binôme, en submodule git. Il n'est jamais modifié ici : le module :manette
// (android/manette) est une enveloppe qui compile ses sources en bibliothèque (AAR).
val manetteCatalog = rootDir.parentFile.resolve("third_party/Manette/gradle/libs.versions.toml")
require(manetteCatalog.isFile) {
    "Submodule Manette absent (${manetteCatalog.parentFile.parentFile}). " +
        "Lancer : git submodule update --init   (premier ajout : scripts/ajouter-submodule-manette.sh <URL>)"
}

dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs {
        // Les versions des dépendances de :manette suivent celles du dépôt du binôme.
        create("manetteLibs") { from(files(manetteCatalog)) }
    }
}
rootProject.name = "javaboy-android"
include(":app")
include(":core")
project(":core").projectDir = file("../core")
include(":manette")
