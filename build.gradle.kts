subprojects {
    apply(plugin = "java")
    repositories { mavenCentral() }
    configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
}
