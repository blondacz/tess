# Copilot / Agent Instructions for TESS

This repo is a small, multi-module Gradle project implementing a tiny Event-Sourcing system in Scala. Use this file to quickly orient AI coding agents to the project's structure, conventions, and common workflows.

Key points
- **Languages & tech:** Scala (Scala 3), Gradle Kotlin DSL, ScalaPB (protobuf -> Scala), gRPC (Java/Scala interop), Borer for JSON, Logback for logging.
- **Modules:** Root includes `app` and `docs` (see `settings.gradle.kts`). `buildSrc` contains Gradle Kotlin DSL helpers.
- **Protobuf/ScalaPB:** Protos live in `app/src/main/proto`. Generated Scala sources are in `app/build/generated/source/proto/main/scalapb` and are explicitly added to the Scala source set in `app/build.gradle.kts`.

Build & run (developer workflows)
- Use the Gradle wrapper from repo root: `./gradlew <task>` (macOS zsh).
- Build the app module: `./gradlew :app:build`.
- Run tests: `./gradlew :app:test` (Scala tests are executed via the Gradle Scala plugin).
- Print project/app version: `./gradlew :app:printVersion` (task defined in `app/build.gradle.kts`).
- If the `application`/`module-app` plugin exposes a run task, try: `./gradlew :app:run`.

Protobuf/codegen notes (important)
- `app/build.gradle.kts` uses the `com.google.protobuf` plugin and ScalaPB plugin. Protoc and scalapb artifacts are specified there (example: `protoc:3.25.1`, scalapb `0.11.20`).
- The Gradle plugin generates Scala + gRPC stubs; the build removes the Java builtin and uses the `scalapb` plugin option `grpc`.
- When editing `.proto` files, re-run `./gradlew :app:build` (or the generateProto tasks) to refresh `build/generated/source/proto/...`.
- Example path for generated classes: `app/build/generated/source/proto/main/scalapb/<package>/...` — import these from Scala sources under `app/src/main/scala`.

Conventions & patterns specific to this repo
- Keep gRPC / protobuf-related dependency versions aligned across Java/Scala gRPC dependencies (the project manually adds Java gRPC libs in `app/build.gradle.kts`).
- Prefer ScalaPB-generated classes rather than hand-written Java proto classes — the build removes the `java` builtin in the proto generation.
- The project uses small, focused modules; `app` is where runtime, protos, and service implementations live; `docs` holds markdown docs under `docs/content`.

Files to consult for context and examples
- `app/build.gradle.kts` — protobuf + ScalaPB configuration, dependencies (gRPC, Borer, Logback), and source set adjustments.
- `settings.gradle.kts` — included modules and project name.
- `gradle.properties` — group, version, and Gradle JVM/config flags.
- `docs/content` — high level design and implementation notes.

Common tasks you might perform as an AI coding agent
- Add or modify proto types: update `app/src/main/proto/*.proto`, run the Gradle generate/build task, update Scala usages in `app/src/main/scala`.
- Update gRPC/ScalaPB versions: change versions in `app/build.gradle.kts` and run a full build; ensure runtime dependencies (grpc-netty, grpc-stub, grpc-protobuf) are aligned.
- Fix build failures related to generated code: check `app/build/generated/source/proto/...` and `sourceSets` config in `app/build.gradle.kts`.

Pitfalls & tips
- The ScalaPB plugin invocation in `app/build.gradle.kts` uses the `unix@sh` scalapb artifact — on non-Unix systems adjust accordingly.
- Don't assume a separate `sbt` build; this project builds with Gradle and uses Gradle's Scala plugin.
- `buildSrc` contains Gradle plugin/accessor code — update here when adding shared Gradle logic.

When in doubt
- Run `./gradlew :app:build --stacktrace` to reproduce errors locally and inspect generated sources.
- Search for examples in `app/src` and `docs/content` for domain-specific patterns.

Feedback
- If anything here is unclear or you'd like more examples (code snippets, exact run commands for a particular task), tell me what you want and I'll iterate.
