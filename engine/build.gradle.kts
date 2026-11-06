plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        // Show the full AssertionError message (expected/actual) in the
        // console. Without this, Gradle prints only "FAILED" and the
        // developer has to open the HTML report to see what was wrong.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Ф1.3.3 (decision C): use the actual test_adventure.json from :app
// in :engine tests. Prevents drift: if the file breaks, :engine tests
// fail before a new user sees a broken sample on first launch.
tasks.named<Copy>("processTestResources") {
    from(rootProject.file("app/src/main/res/raw/test_adventure.json"))
}

// Ф1.4: golden-IR regeneration. After an intentional IR change, run
// `./gradlew :engine:updateGolden` to rewrite
// engine/src/test/resources/golden_ir.json from the current
// test_adventure.json. Commit the updated golden with the IR change.
//
// GoldenWriter is a test-source utility (not part of :engine public API,
// not shipped in the jar), so the classpath is sourceSets.test.
tasks.register<JavaExec>("updateGolden") {
    group = "verification"
    description = "Rewrites engine/src/test/resources/golden_ir.json from the current IR."
    dependsOn(tasks.named("processTestResources"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.myapp.adventure.engine.format.GoldenWriterKt")
    // Without this, JavaExec runs from the module directory, and the
    // repository-relative path "engine/src/test/resources/golden_ir.json"
    // would resolve to "<module>/engine/src/..." instead of the intended
    // file. Pinning workingDir to the repository root keeps the path in
    // GoldenWriter meaningful regardless of where the task is launched from.
    workingDir = rootProject.projectDir
}