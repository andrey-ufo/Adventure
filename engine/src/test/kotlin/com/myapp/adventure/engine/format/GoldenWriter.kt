package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.parse.ParseResult
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Regenerates `engine/src/test/resources/golden_ir.json` from the current
 * parser and the current `test_adventure.json`.
 *
 * **Not** a test. Not part of `:engine` public API. Not shipped in the jar
 * (this file lives in `src/test/kotlin`). Its only purpose is to serve
 * `./gradlew :engine:updateGolden`, which developers run **after an
 * intentional IR change** and commit alongside the change.
 *
 * **Never** edit `golden_ir.json` by hand:
 * 1. Hand-edits can miss fields or introduce typos that pass a diff-by-eye.
 * 2. The golden is compared **semantically** by `GoldenIrTest` — hand-edit
 *    that preserves semantics but changes formatting is fine, but a
 *    hand-edit that *changes* semantics is a bug, not a fix.
 *
 * **Workflow when you change the IR:**
 * 1. Change the IR (files in `:engine/.../ir/`).
 * 2. Run `./gradlew :engine:updateGolden`.
 * 3. `git diff engine/src/test/resources/golden_ir.json` — verify the diff
 *    is exactly what you expected. If it contains unexpected fields, you
 *    have an unintended IR change; investigate before committing.
 * 4. Commit the golden alongside the IR change.
 *
 * **Workflow when you do NOT change the IR:**
 * If `GoldenIrTest` fails and you did not touch `:engine/.../ir/`, one of:
 * - Someone else changed the IR (in this branch, via merge). Review their
 *   change, then run `updateGolden` if it is intentional.
 * - The parser changed (`JsonToIr`, `ConditionParser`). The golden
 *   captures parser output too, by design: a parser change that alters
 *   IR output is a breaking change for every adventure file.
 * - `test_adventure.json` changed. That is also a deliberate act; update
 *   the golden.
 */
private const val GOLDEN_PATH = "engine/src/test/resources/golden_ir.json"

private val PRETTY_JSON = Json {
    prettyPrint = true
    encodeDefaults = false
}

public fun main() {
    val classLoader = object {}.javaClass.classLoader
    val stream = classLoader.getResourceAsStream("test_adventure.json")
        ?: error(
            "test_adventure.json not found on classpath. " +
                    "The copy task in engine/build.gradle.kts should have placed it " +
                    "in engine/build/resources/test/. Make sure :engine:processTestResources ran.",
        )
    val rawJson = stream.bufferedReader().use { it.readText() }

    val result = JsonToIr.parse(rawJson)
    val adventure: Adventure = when (result) {
        is ParseResult.Success -> result.value
        is ParseResult.Failure -> {
            System.err.println("Cannot regenerate golden: test_adventure.json failed to parse.")
            result.errors.forEach { System.err.println("  ${it.message}") }
            kotlin.system.exitProcess(1)
        }
    }

    val text = PRETTY_JSON.encodeToString(Adventure.serializer(), adventure)
    val out = File(GOLDEN_PATH)
    out.parentFile.mkdirs()
    out.writeText(text)
    println("Wrote $GOLDEN_PATH (${text.length} chars, ${adventure.chapters.size} chapters)")
}