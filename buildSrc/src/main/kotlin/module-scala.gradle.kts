plugins {
    `java-library`
    `jvm-test-suite`
    scala
}

repositories {
    mavenCentral()
 }

scala {
    scalaVersion = "3.6.3"
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


testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(libs.findLibrary("scala-test").get())
                implementation(libs.findLibrary("junit-platform-launcher").get())
                implementation(libs.findLibrary("junit-platform-engine").get())
                implementation(libs.findLibrary("junit-scala-test-plus").get())
            }
        }
    }
}




