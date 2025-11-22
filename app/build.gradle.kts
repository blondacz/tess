plugins {
    `module-scala`
    `module-app`
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapters:octopus"))
    implementation(project(":adapters:audit"))
    implementation(project(":adapters:relay"))
    implementation(project(":adapters:cylinder-temperature"))
    implementation(libs.pekko.http)
    implementation(libs.pekko.http.backend)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.actor)
    implementation(libs.pekko.circe)
    implementation(libs.bundles.circe)
    implementation(libs.bundles.prometheus)


    implementation(libs.logback)
    implementation(libs.sttp.core)

    testImplementation(libs.pekko.testkit)

    testImplementation(libs.pekko.stream.testkit)
    testImplementation(libs.pekko.http.testkit)
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}

