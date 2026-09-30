// Cœur d'émulation : Java pur, sans AWT ni Android. Réutilisé tel quel par :desktop et :app.
plugins { `java-library` }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

// Le cœur vise le bytecode Java 17 (records + sealed) pour rester consommable par D8/Android ;
// les tests, eux, utilisent Java 21 (switch à motifs).
tasks.compileJava { options.release.set(17) }
