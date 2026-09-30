// Projet Android Studio : ouvrir CE dossier. Le module :core est celui du dépôt (../core).
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "javaboy-android"
include(":app")
include(":core")
project(":core").projectDir = file("../core")
