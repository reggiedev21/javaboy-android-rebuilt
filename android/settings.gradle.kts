// Projet Android Studio : ouvrir CE dossier. Le module :core est celui du dépôt (../core).
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "javaboy-android"
include(":app")
include(":core")
project(":core").projectDir = file("../core")
