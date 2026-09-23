plugins {
    java
    application
}

group = "javaboy"
version = "0.92-modernisation"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

application {
    mainClass.set("javaboy.JavaBoy")
}

tasks.withType<JavaCompile> {
    options.encoding = "ISO-8859-1"
}
