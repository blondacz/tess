plugins {
    `java-library`
    `jvm-test-suite`
}

repositories {
    mavenCentral()
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

//Gradle issue: https://github.com/gradle/gradle/issues/17236
tasks.processResources { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
tasks.jar { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
// \Gradle issue

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")





