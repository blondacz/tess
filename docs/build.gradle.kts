plugins {
    `module-scala`
}

configurations.create("plantuml")
dependencies {
    "plantuml"(libs.puml)
}

val renderPuml by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Render PUML diagrams under docs/content/diagrams to PNG & SVG"
    classpath = configurations.getByName("plantuml")
    mainClass.set("net.sourceforge.plantuml.Run")
    args = listOf("-tpng", "-tsvg", "content/diagrams")
}