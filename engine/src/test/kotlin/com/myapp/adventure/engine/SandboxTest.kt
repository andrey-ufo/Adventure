package com.myapp.adventure.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Ф3.7 — sandbox criterion for `:engine` main sources.
 *
 * **What this test is.** A static scan of every `.kt` file under
 * `engine/src/main/kotlin` for imports and identifiers that break the
 * engine's determinism / purity contract. The engine is the only
 * component that must run identically on device, in JVM tests, and
 * (future) in a JS runtime (E1.base); anything that touches the wall
 * clock, the filesystem, the network, the OS, or non-deterministic
 * randomness would make that impossible.
 *
 * **DoD (Ф3.7, checklist-tracker).** "`evaluate()` в `:engine` не
 * имеет `java.lang.reflect`, `System.*`, файлового IO; статический
 * тест подтверждает; это **не** полный Q1 (экранирование — Ф10), но
 * sandbox готов."
 *
 * **What is forbidden** (see [forbiddenImports] / [forbiddenIdentifiers]):
 *
 * - **Reflection** (`java.lang.reflect`) — sandbox must not inspect
 *   or invoke arbitrary code at runtime.
 * - **File I/O** (`java.io.*`, `java.nio.file.*`) — the engine is a
 *   pure function library; persistence lives in `:app`
 *   (`ProgressManager`, `StepHistoryManager`, `Logger`).
 * - **Network** (`java.net.*`) — the engine has no server calls, and
 *   must not gain any.
 * - **Android** (`android.*`) — `:engine` builds without the Android
 *   SDK (`engine/build.gradle.kts` has no `com.android.*` plugin).
 *   The import check is a cheap redundant guard for that invariant.
 * - **Process execution** (`Runtime.getRuntime`, `ProcessBuilder`) —
 *   side effects on the host OS.
 * - **System side effects** (`System.exit`, `System.getenv`,
 *   `System.getProperties`) — the engine must not depend on
 *   environment; anything that does, gets injected (e.g. a future
 *   `RandomSource` for Ф12 RNG, a `Clock` for time).
 * - **Non-deterministic randomness** (`kotlin.random.Random`,
 *   `java.util.Random`) — using them makes tests flaky and
 *   save/restore non-deterministic. Ф12 will inject a `RandomSource`
 *   explicitly.
 * - **Wall-clock time** (`System.currentTimeMillis`,
 *   `System.nanoTime`) — same reason. If a future feature needs "time
 *   of day" (e.g. "the door is open only after 22:00"), a `Clock` will
 *   be injected; direct `System.currentTimeMillis` from `:engine` is a
 *   sandbox violation.
 *
 * **What is allowed, and why:**
 *
 * - **`java.util.UUID`** — uses `SecureRandom` internally, but UUIDs
 *   are not used in `evaluate` / `Engine.step` today. If a future
 *   feature needs ID generation for logging, that is fine — the
 *   sandbox concern is *evaluation determinism*, not all randomness
 *   everywhere. (If `UUID.randomUUID()` ever appears inside
 *   `Evaluator` / `Engine`, that will be caught by code review, and
 *   the pattern list here can be extended.)
 * - **`java.time.*`** — `LocalDate`, `Duration`, `Instant` as *value
 *   types* are deterministic and side-effect-free. However,
 *   `LocalDate.now()` / `Instant.now()` / `ZonedDateTime.now()`
 *   **indirectly call `System.currentTimeMillis()`**. Today `:engine`
 *   does not use any of the `*.now()` variants, so the sandbox is
 *   clean. **TODO (roadmap):** when the engine first uses time (e.g.
 *   a "night only" invariant), add `LocalDate.now` / `Instant.now` /
 *   etc. to [forbiddenIdentifiers], and inject a `Clock` parameter
 *   instead of reading the wall clock.
 * - **`kotlinx.serialization`** — the `@Serializable` compiler plugin
 *   generates code without runtime reflection for all common cases.
 *   Edge cases (polymorphic deserialization of certain shapes) can
 *   fall back to reflective fallback inside the library. **This is
 *   not our code** — the sandbox check scans our sources, not our
 *   dependencies. If the library ever uses reflection at runtime, that
 *   is outside the scope of this criterion (it would show up as a
 *   failure on a JS runtime that has no reflection, in E1.base).
 * - **`java.util.Locale`** — deterministic value type; if locale-
 *   dependent formatting is ever needed in the engine, that is a
 *   design decision (and would violate i18n separation anyway), not
 *   a sandbox violation.
 * - **`kotlin.*`** — stdlib, no side effects.
 *
 * **How the check works.** Line-based scan:
 *
 * 1. For every `.kt` file under `engine/src/main/kotlin`:
 *    - For every line whose trimmed form starts with `import`, apply
 *      every regex in [forbiddenImports].
 *    - For every line that is **not** a comment (does not start with
 *      `//`, `*`, or a block-comment opener after trimming), apply
 *      every regex in [forbiddenIdentifiers].
 * 2. Collect all violations with file path and line number.
 * 3. If non-empty, `fail()` with the full list — a single failing
 *    test run shows every offender at once, not just the first.
 *
 * **Limitations of the line-based approach (documented, not bugs):**
 *
 * - **Comments are approximated, not parsed.** A KDoc line starting
 *   with ` * System.exit` is skipped (a valid comment); a string
 *   literal `"System.exit"` on a code line is *not* skipped and would
 *   be a false positive. The `:engine` codebase does not contain such
 *   strings today, and the probability of introducing one is low.
 * - **Block comments on the same line as code** are not handled: a
 *   line like `val x = 1 // System.exit` would be flagged. Not the
 *   case in practice.
 * - **Multi-line strings** (`"""..."""`) are not handled. Not used in
 *   `:engine` today.
 *
 * If any of these become real, replace with an AST-based scan
 * (`kotlin-compiler-embeddable` + `KtFile.visitChildren`) — an
 * overkill for the current code size and value, but a clean upgrade
 * path.
 *
 * **What this test does NOT cover** (deliberately, per Ф3.7 DoD):
 *
 * - **HTML escaping / XSS** — Q1 proper, Ф10.2. That is a
 *   `:app` / exporter concern, not `:engine`.
 * - **JS runtime sandbox** (`window` / `document` / `eval` / `fetch`)
 *   — E1.base (Ф8.9). Same principle, different source language.
 * - **Dependency-level reflection** — see "kotlinx.serialization"
 *   above.
 * - **Threat model / docs** — `docs/security.md` (Ф10.9). This test
 *   is the machine-checkable part; the human-readable side lands with
 *   Ф10.
*/
class SandboxTest {

/**
 * Import patterns forbidden in `:engine/src/main/kotlin`.
 *
 * Each entry pairs a short label (for failure messages) with a
 * regex that matches the `import`-line. Regex is anchored at
 * `^import\s+` so that `// import java.io.File` (a comment) does
 * not match — the caller already filters comments before applying
 * this list, but the anchor makes the intent explicit.
*/
private val forbiddenImports: List<Pair<String, Regex>> = listOf(
"reflection" to Regex("""^import\s+java\.lang\.reflect\b"""),
"java.io" to Regex("""^import\s+java\.io\b"""),
"java.nio.file" to Regex("""^import\s+java\.nio\.file\b"""),
"network" to Regex("""^import\s+java\.net\b"""),
"android" to Regex("""^import\s+android\b"""),
"kotlin.random.Random" to Regex("""^import\s+kotlin\.random\.Random\b"""),
"java.util.Random" to Regex("""^import\s+java\.util\.Random\b"""),
)

/**
 * Identifier patterns forbidden in non-comment lines of
 * `:engine/src/main/kotlin`.
 *
 * These cover cases the import scan cannot catch: `System` and
 * `Runtime` live in `java.lang`, auto-imported, so `System.exit`
 * would appear without an `import`. `ProcessBuilder` is in
 * `java.lang` too. The regex is anchored with `\b` on both sides
 * so `System.exitCode` (hypothetical) does not match
 * `System.exit` and `ProcessBuilderFactory` does not match
 * `ProcessBuilder` — deliberate.
*/
private val forbiddenIdentifiers: List<Pair<String, Regex>> = listOf(
"System.exit" to Regex("""\bSystem\.exit\b"""),
"System.getenv" to Regex("""\bSystem\.getenv\b"""),
"System.getProperties" to Regex("""\bSystem\.getProperties\b"""),
"System.currentTimeMillis" to Regex("""\bSystem\.currentTimeMillis\b"""),
"System.nanoTime" to Regex("""\bSystem\.nanoTime\b"""),
"Runtime.getRuntime" to Regex("""\bRuntime\.getRuntime\b"""),
"ProcessBuilder" to Regex("""\bProcessBuilder\b"""),
)

@Test
fun `main sources have no forbidden imports or identifiers`() {
val root = locateMainSources()
val violations = mutableListOf<String>()

root.walkTopDown()
.filter { it.isFile && it.extension == "kt" }
.sortedBy { it.relativeTo(root).path }
.forEach { file ->
val relative = file.relativeTo(root).path
file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
val lineNumber = index + 1
val trimmed = line.trimStart()
val isComment = trimmed.startsWith("//") ||
trimmed.startsWith("*") ||
trimmed.startsWith("/*")
if (!isComment && trimmed.startsWith("import")) {
for ((label, regex) in forbiddenImports) {
if (regex.containsMatchIn(line)) {
violations += "$relative:$lineNumber — forbidden import ($label): ${line.trim()}"
}
}
}
if (!isComment) {
for ((label, regex) in forbiddenIdentifiers) {
if (regex.containsMatchIn(line)) {
violations += "$relative:$lineNumber — forbidden identifier ($label): ${line.trim()}"
}
}
}
}
}

if (violations.isNotEmpty()) {
fail(
"Sandbox violated: :engine main sources must not use reflection, " +
"IO, network, process execution, wall-clock time, or " +
"non-deterministic randomness (see SandboxTest KDoc for the " +
"full rule). If this is intentional, add an exemption with a " +
"written rationale — do not silently extend the pattern list.\n" +
"Violations (${violations.size}):\n" +
violations.joinToString("\n") { "  • $it" },
)
}
}

/**
 * Sanity check: without this, a wrong [locateMainSources] path or a
 * silently-empty scan would pass [main sources have no forbidden
 * imports or identifiers] vacuously.
 *
 * Pins:
 * - the source root is discoverable;
 * - it contains a plausible number of `.kt` files (≥ 10 — the
 *   engine currently has ~20, so the check catches "everything
 *   deleted");
 * - `Evaluator.kt` exists at the root of the engine package — the
 *   file the sandbox criterion is named after. If it is missing,
 *   either the package moved (update this test) or the scan is
 *   looking at the wrong directory.
*/
@Test
fun `main sources exist and are discoverable`() {
val root = locateMainSources()
assertTrue(
root.isDirectory,
"Expected directory: ${root.absolutePath}",
)
val ktFiles = root.walkTopDown()
.filter { it.isFile && it.extension == "kt" }
.toList()
assertTrue(
ktFiles.size >= 10,
"Expected at least 10 .kt files under ${root.absolutePath}, found ${ktFiles.size}. " +
"If the engine was refactored, update this threshold; if the path is wrong, " +
"fix locateMainSources().",
)
assertTrue(
ktFiles.any { it.name == "Evaluator.kt" },
"Evaluator.kt not found under ${root.absolutePath}. " +
"Either the package layout changed (update this test) or the scan is " +
"looking at the wrong directory.",
)
}

/**
 * Locates `engine/src/main/kotlin` from the test's working
 * directory.
 *
 * Two candidates, tried in order:
 * - `src/main/kotlin` — Gradle runs `:engine:test` with
 *   `workingDir = engine/` (module directory), so this resolves
 *   directly.
 * - `engine/src/main/kotlin` — a fallback for running the test from
 *   the repository root (e.g. IDE "Run" on the test class without
 *   the Gradle project working directory).
 *
 * Validates by checking that the engine package subdirectory
 * exists; a directory with no `.kt` files is not a valid root.
*/
private fun locateMainSources(): File {
val candidates = listOf(
File("src/main/kotlin"),
File("engine/src/main/kotlin"),
)
for (candidate in candidates) {
if (candidate.isDirectory &&
candidate.resolve("com/myapp/adventure/engine").isDirectory
) {
return candidate
}
}
error(
"Cannot locate :engine main sources. Tried: " +
candidates.joinToString { it.absolutePath } +
". Current working directory: ${File(".").absolutePath}. " +
"If Gradle ran the test from an unexpected location, add a candidate " +
"in locateMainSources().",
)
}
}