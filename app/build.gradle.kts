plugins {
    `module-scala`
    `module-app`
}

dependencies {
    implementation(libs.logback)
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}

