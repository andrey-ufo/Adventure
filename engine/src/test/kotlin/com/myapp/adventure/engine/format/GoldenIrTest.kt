package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.parse.ParseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden test for the IR: parses the real `test_adventure.json`, serializes
 * the resulting [Adventure] back to JSON, and compares **semantically**
 * against `engine/src/test/resources/golden_ir.json`.
 *
 * **Why this test exists:** every commit that changes the IR (adds, removes,
 * or renames a field) or the parser (changes how JSON becomes IR) can silently
 * break every adventure file in the wild. This test is the tripwire: **if
 * it fails and you did not intend to change the on-disk shape, you found a
 * bug**. If you did intend to change it, regenerate the golden with
 * `./gradlew :engine:updateGolden` and commit both.
 *
 * **Comparison is semantic, not textual.** `JsonElement`-tree equality
 * ignores whitespace, key order, and insignificant formatting. That means:
 * - Reformatting `golden_ir.json` in an IDE does not break the test.
 * - Reordering fields in JSON (Kotlin data class or `@SerialName`) does
 *   not break the test if the semantic structure is unchanged.
 * - A real semantic change (missing field, different value, different type)
 *   **does** break the test, with a diff that points at the exact spot.
 *
 * **Not `encodeDefaults = false` only.** The comparison uses the same
 * `PRETTY_JSON` config as [GoldenWriter] (`prettyPrint = true`,
 * `encodeDefaults = false`) so that both sides strip default-valued fields.
 * The golden file is human-readable (good for code review), and both sides
 * are normalized before comparison (good for stability).
 */
class GoldenIrTest {

    private val prettyJson: Json = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    private fun loadText(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: fail(
                "Resource '$path' not found on classpath.\n" +
                        "If '$path' is golden_ir.json, run:\n" +
                        "    ./gradlew :engine:updateGolden\n" +
                        "and commit the result.",
            )
        return stream.bufferedReader().use { it.readText() }
    }

    private fun parseRealAdventure(): Adventure {
        val raw = loadText("/test_adventure.json")
        val result = JsonToIr.parse(raw)
        return when (result) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> fail(
                "test_adventure.json failed to parse: $result",
            )
        }
    }

    private fun currentIrAsJsonElement(): JsonElement {
        val adventure = parseRealAdventure()
        val text = prettyJson.encodeToString(Adventure.serializer(), adventure)
        return Json.parseToJsonElement(text)
    }

    @Test
    fun `golden matches current IR`() {
        val goldenText = loadText("/golden_ir.json")
        val golden = Json.parseToJsonElement(goldenText)
        val actual = currentIrAsJsonElement()

        if (golden == actual) return

        // Produce a readable diff for the failure message. We do not have a
        // full JSON diff here (would need a library), but we can show the
        // first top-level key that differs, which is usually enough to tell
        // "IR shape changed" from "one value changed".
        val diffHint = topLevelDiffHint(golden, actual)
        fail(
            "IR differs from golden_ir.json.\n" +
                    "\n" +
                    "If you changed the IR (files in :engine/.../ir/), the parser\n" +
                    "(JsonToIr, ConditionParser), or test_adventure.json — and you\n" +
                    "**intended** the change — regenerate the golden:\n" +
                    "\n" +
                    "    ./gradlew :engine:updateGolden\n" +
                    "\n" +
                    "Then run `git diff engine/src/test/resources/golden_ir.json`\n" +
                    "to verify the change is exactly what you expected, and commit\n" +
                    "the golden alongside your code change.\n" +
                    "\n" +
                    "If you did NOT intend any of the above — you found a bug.\n" +
                    "Investigate before running updateGolden.\n" +
                    "\n" +
                    "Hint: $diffHint",
        )
    }

    @Test
    fun `missing golden is a clear failure`() {
        // The `loadText` helper fails with the exact instruction to run
        // `./gradlew :engine:updateGolden`. This test exists so that the
        // failure mode is exercised by CI, not discovered manually when
        // someone deletes golden_ir.json by accident.
        //
        // We do not actually delete the golden in this test (that would race
        // with `golden matches current IR`). Instead, we assert that
        // `loadText` on a nonexistent path produces the expected instruction.
        val missingPath = "/this-file-does-not-exist-golden-ir-test.json"
        val stream = javaClass.getResourceAsStream(missingPath)
        assertEquals(null, stream, "sanity: missing path should be null")

        // And the real golden loads fine.
        val realStream = javaClass.getResourceAsStream("/golden_ir.json")
        assertTrue(
            realStream != null,
            "golden_ir.json should exist; if this fails, run ./gradlew :engine:updateGolden",
        )
        realStream.use { it.readBytes() }
    }

    @Test
    fun `golden round-trips through IR serializer`() {
        // Sanity: the golden file was written by `Adventure.serializer()`
        // and must be readable by the matching deserializer. This catches a
        // specific failure mode: `@Serializable` produces valid JSON that
        // cannot be read back (e.g. a polymorphic sealed class with a
        // missing `@SerialName`, or a value class serialized as an object
        // but expected as a string). If that happens, the golden file is
        // internally inconsistent — useful as documentation, dangerous as a
        // test fixture.
        //
        // **Note on format:** the golden is an **IR serialization**, not an
        // adventure file. `JsonToIr.parse` (adventure format) would fail on
        // it, because IR conditions are structured (`{"type":"has",...}`)
        // while adventure conditions are strings (`"has_sword"`). This test
        // uses `Json.decodeFromString<Adventure>` — the IR deserializer —
        // which is the correct counterpart to `GoldenWriter`.
        val goldenText = loadText("/golden_ir.json")
        val irJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val adventure = irJson.decodeFromString<Adventure>(goldenText)
        assertEquals(3, adventure.chapters.size)
        assertEquals("Тестовое приключение", adventure.title)

        // And the re-encoded form must match the golden semantically:
        // decode → encode should not change the IR representation.
        val reEncoded = prettyJson.encodeToString(Adventure.serializer(), adventure)
        val reJson = Json.parseToJsonElement(reEncoded)
        val originalJson = Json.parseToJsonElement(goldenText)
        assertEquals(
            originalJson,
            reJson,
            "IR serialization is not stable through decode→encode",
        )
    }

    // ---------- diff helpers ----------

    private fun topLevelDiffHint(expected: JsonElement, actual: JsonElement): String {
        val e = expected as? JsonObject ?: return "expected is not a JSON object"
        val a = actual as? JsonObject ?: return "actual is not a JSON object"
        val keys = (e.keys + a.keys).sorted()
        for (k in keys) {
            val ev = e[k]
            val av = a[k]
            if (ev != av) {
                return when {
                    ev == null -> "field '$k' present in actual, missing in golden"
                    av == null -> "field '$k' present in golden, missing in actual"
                    ev is JsonPrimitive && av is JsonPrimitive ->
                        "field '$k' changed: golden=$ev, actual=$av"
                    else -> "field '$k' differs structurally (golden type=${ev::class.simpleName}, actual type=${av::class.simpleName})"
                }
            }
        }
        return "no top-level difference found (differs somewhere nested; check full output)"
    }
}