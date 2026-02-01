import com.google.protobuf.gradle.*  // <-- important
import org.gradle.api.tasks.scala.ScalaCompile

plugins {
    `module-scala`
    `module-app`
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("org.rocksdb:rocksdbjni:9.7.3")
    implementation(libs.logback)

    // ScalaPB runtime
    implementation("com.thesamet.scalapb:scalapb-runtime_3:0.11.20")
    // ScalaPB gRPC runtime
    implementation("com.thesamet.scalapb:scalapb-runtime-grpc_3:0.11.20")

    // gRPC runtime (Java) – try to keep versions aligned, e.g. all 1.75.0
    implementation("io.grpc:grpc-netty-shaded:1.75.0")
    implementation("io.grpc:grpc-stub:1.75.0")
    implementation("io.grpc:grpc-protobuf:1.75.0")
    implementation("io.bullet:borer-core_3:1.16.2")
    implementation("io.bullet:borer-derivation_3:1.16.2")
    implementation("io.bullet:borer-compat-circe_3:1.16.2")
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}

// Make sure the Scala compiler sees the generated sources
sourceSets {
    val main by getting {
        scala {
            srcDir(project.layout.buildDirectory.dir("generated/source/proto/main/scalapb"))
        }
    }
}

// Silence Scala warnings coming from generated ScalaPB sources while keeping warnings for handwritten code.
tasks.withType<ScalaCompile>().configureEach {
    val existing = scalaCompileOptions.additionalParameters.orEmpty()
    scalaCompileOptions.additionalParameters = existing + "-Wconf:src=.*/build/generated/.*:silent"
}

protobuf {
    protoc {
        // Use any recent protoc version
        artifact = "com.google.protobuf:protoc:3.25.1"
    }

    plugins {
        // ScalaPB protoc plugin
        id("scalapb") {
            artifact = "com.thesamet.scalapb:protoc-gen-scala:0.11.20:unix@sh"

        }
    }

    generateProtoTasks {
        // Configure all proto generation tasks to use ScalaPB + gRPC
        all().configureEach {
            builtins {
                remove("java")
            }
            plugins {
                id("scalapb") {
                    option("grpc")   // generate gRPC stubs
                }
            }
        }
    }
}
