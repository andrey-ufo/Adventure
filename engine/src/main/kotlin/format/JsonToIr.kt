package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.format.dto.AdventureDto
import com.myapp.adventure.engine.format.dto.ChapterDto
import com.myapp.adventure.engine.format.dto.ChoiceDto
import com.myapp.adventure.engine.format.dto.SceneDto
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.DeathScene
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagDefinition
import com.myapp.adventure.engine.ir.Gate
import com.myapp.adventure.engine.parse.ConditionParser
import com.myapp.adventure.engine.parse.ParseError
import com.myapp.adventure.engine.parse.ParseResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.myapp.adventure.engine.ir.ChoiceId

/**
 * Parses the adventure JSON file into the typed IR ([Adventure]).
 *
 * **Single entry point for JSON → IR.** This is the only public function
 * in `:engine` that turns raw JSON text into an [Adventure]. It is used by:
 * - the reader (`:app`, Ф1.7) to load an adventure for playing;
 * - the editor (`:app`, Ф9.2) to validate an author's in-progress JSON;
 * - the analyzer (Ф1.6) after parsing, to check reachability and duplicates;
 * - tests (`:engine`, Ф1.3.4, Ф1.4), including the real `test_adventure.json`
 *   shipped to users.
 *
 * Do **not** write a second parser alongside this one. If a new format is
 * needed (YAML, binary, a future JS runtime), either add a `parse` overload
 * here or create a new top-level `object <Format>ToIr` with the same
 * `ParseResult<Adventure>` contract. Two parsers with divergent behaviour
 * is exactly the bug N2/Ф3 exists to prevent.
 *
 * **Parser vs Analyzer boundary** (do not blur this):
 * - **Parser** (this object): "can the file be turned into an
 *   [Adventure]?" — syntax of JSON, syntax of conditions, blank required
 *   fields, empty chapters. Nothing about the **meaning** of the file.
 * - **Analyzer** (Ф1.6): "is the resulting IR coherent?" — reachability,
 *   duplicate `scene.id`, `next_scene` pointing to a missing scene,
 *   `has_X` on a numeric variable (type checker, Ф2.2).
 *
 * Consequence: a JSON file that parses **successfully** may still be
 * rejected by the analyzer. That is by design — the two layers answer
 * different questions, and mixing them would make both harder to test.
 *
 * **Error strategy** (decision B from Ф1.3.3):
 * - **JSON syntax errors** (malformed JSON, wrong type for a field) — the
 *   underlying decoder throws `SerializationException`; we catch it once
 *   and return a single [ParseError.Syntax]. These are rare in practice:
 *   the editor always emits valid JSON, authors do not hand-write it.
 * - **Semantic errors** (blank ids, empty chapters, unparseable
 *   conditions) — accumulated into a [ParseResult.Failure] with **all**
 *   errors at once. Authors see every problem in one pass, not one at a
 *   time. This is the common case, so it is worth the extra code.
 *
 * **`path` convention:** `chapters[i:<id>].scenes[j:<id>].choices[k]`,
 * with `:<id>` omitted when the id is blank. Example:
 * `chapters[1:forest].scenes[0:start].choices[2]`. The author sees the
 * chapter and scene by name in the editor error panel (Ф9.2), not just
 * an index.
 *
 * **Not in scope here** (deferred):
 * - Type checking of variables (`has_X` on numeric variable) — Ф2.2.
 * - Duplicate `scene.id` detection — Ф2 / `Analyzer`.
 * - `next_scene` reachability — Ф2 / `Analyzer`.
 * - Migration of `version < CURRENT_VERSION` — Ф5.3 (for now, files with
 *   no `version` get [Adventure.CURRENT_VERSION]; files with any `version`
 *   keep it as-is).
 */
public object JsonToIr {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Parses [text] (raw JSON contents of an adventure file) into an
     * [Adventure], or returns [ParseResult.Failure] with one or more
     * [ParseError]s.
     */
    public fun parse(text: String): ParseResult<Adventure> {
        val dto: AdventureDto = try {
            json.decodeFromString<AdventureDto>(text)
        } catch (e: SerializationException) {
            return ParseResult.Failure(
                listOf(
                    ParseError.Syntax(
                        message = "Malformed JSON: ${e.message ?: "unknown syntax error"}",
                        path = null,
                    ),
                ),
            )
        }

        val errors = mutableListOf<ParseError>()
        val adventure = buildAdventure(dto, errors)
        return if (errors.isEmpty()) {
            ParseResult.Success(adventure)
        } else {
            ParseResult.Failure(errors)
        }
    }

    // ---------- DTO → IR ----------

    private fun buildAdventure(
        dto: AdventureDto,
        errors: MutableList<ParseError>,
    ): Adventure {
        if (dto.chapters.isEmpty()) {
            errors += ParseError.Structure(
                message = "Adventure has no chapters",
                path = null,
            )
        }
        // Version migration (v1 → v2, Ф1.5.4): gates moved from chapters
        // to scenes. A v1 file has `chapter.required_steps`; the migration
        // moves `chapter[i].required_steps = N > 0` onto the **last scene
        // of chapter i-1** as `Gate(N)`. The first chapter's
        // `required_steps` is dropped (it was always 0 in practice; even if
        // set, there is no "previous chapter" to gate).
        //
        // Files without `version` (pre-IR) are treated as v1 — same
        // migration. Files with `version >= 2` have `required_steps` on
        // chapters only as a leftover; we ignore it (it should be absent in
        // a well-formed v2 file).
        val effectiveVersion = dto.version ?: 1
        val lastChapterIndex = dto.chapters.lastIndex
        val chapters = dto.chapters.mapIndexed { index, chapterDto ->
            buildChapter(
                dto = chapterDto,
                index = index,
                errors = errors,
                // v1 → v2: `chapter[i].required_steps = N > 0` means
                // "walk N steps to enter chapter i". In v2 the gate lives
                // on the **last scene of chapter i-1** (the scene the player
                // must leave to enter chapter i). So for chapter `index`
                // we pass `required_steps` of chapter `index + 1`.
                previousChapterLastSceneGate = if (index < lastChapterIndex && effectiveVersion < 2) {
                    dto.chapters[index + 1].requiredSteps
                        .takeIf { it > 0 }
                        ?.let { Gate(it) }
                } else {
                    null
                },
            )
        }
        val deathScene = dto.deathScene?.let { d ->
            DeathScene(
                id = SceneId(d.id),
                text = d.text,
                allowStepBack = d.allowStepBack,
                allowChapterRestart = d.allowChapterRestart,
                allowAdventureRestart = d.allowAdventureRestart,
            )
        }
        return Adventure(
            version = dto.version ?: Adventure.CURRENT_VERSION,
            title = dto.title,
            author = dto.author,
            description = dto.description,
            variables = emptyMap(), // Ф2.8: variables are not in the JSON yet
            tags = dto.tags.map { t ->
                TagDefinition(
                    name = t.name,
                    displayName = t.displayName,
                    category = t.category,
                    visible = t.visible,
                )
            },
            initialState = InitialState(
                flags = dto.initialState.flags,
                stats = dto.initialState.stats,
            ),
            deathScene = deathScene,
            chapters = chapters,
        )
    }

    private fun buildChapter(
        dto: ChapterDto,
        index: Int,
        errors: MutableList<ParseError>,
        /**
         * v1 → v2 migration: a non-null gate to attach to the **last scene**
         * of this chapter. This comes from `chapter[index+1].required_steps`
         * in a v1 file, or from `null` for v2 files (and for the last
         * chapter).
         */
        previousChapterLastSceneGate: Gate? = null,
    ): Chapter {
        val path = chapterPath(index, dto.id)
        if (dto.scenes.isEmpty()) {
            errors += ParseError.Structure(
                message = "Chapter has no scenes",
                path = path,
            )
        }
        val lastIndex = dto.scenes.lastIndex
        val scenes = dto.scenes.mapIndexed { sceneIndex, sceneDto ->
            val built = buildScene(sceneDto, path, sceneIndex, errors)
            // Apply v1 → v2 migration gate on the last scene of this chapter
            // (if any). A scene that already has its own gate wins — the
            // author's intent (v2) overrides the legacy (v1) interpretation.
            if (sceneIndex == lastIndex && previousChapterLastSceneGate != null && built.gate == null) {
                built.copy(gate = previousChapterLastSceneGate)
            } else {
                built
            }
        }
        return Chapter(
            id = dto.id,
            title = dto.title,
            scenes = scenes,
        )
    }

    private fun buildScene(
        dto: SceneDto,
        parentPath: String,
        index: Int,
        errors: MutableList<ParseError>,
    ): Scene {
        val path = scenePath(parentPath, index, dto.id)
        if (dto.id.isBlank()) {
            errors += ParseError.Structure(
                message = "Scene id is blank",
                path = path,
            )
        }
        val effects = buildEffects(dto.setFlags, dto.removeFlags, dto.modifyStats)
        val choices = dto.choices.mapIndexed { choiceIndex, choiceDto ->
            buildChoice(choiceDto, dto.id, path, choiceIndex, errors)
        }
        val gate = dto.gate?.let { raw ->
            if (raw <= 0) {
                errors += ParseError.Structure(
                    message = "Scene gate must be a positive number of steps, got $raw",
                    path = "$path.gate",
                )
                null
            } else {
                Gate(raw)
            }
        }
        return Scene(
            id = SceneId(dto.id),
            text = dto.text,
            effects = effects,
            choices = choices,
            gate = gate,
        )
    }

    private fun buildChoice(
        dto: ChoiceDto,
        sceneId: String,
        parentPath: String,
        index: Int,
        errors: MutableList<ParseError>,
    ): Choice {
        val path = "$parentPath.choices[$index]"
        if (dto.text.isBlank()) {
            errors += ParseError.Structure(
                message = "Choice text is blank",
                path = path,
            )
        }
        if (dto.nextScene.isBlank()) {
            errors += ParseError.Structure(
                message = "Choice next_scene is blank",
                path = path,
            )
        }
        val condition = dto.condition?.let { raw ->
            when (val result = ConditionParser.parse(raw)) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> {
                    result.errors.forEach { err ->
                        errors += if (err is ParseError.ConditionError) {
                            err.copy(path = "$path.condition")
                        } else {
                            err
                        }
                    }
                    null
                }
            }
        }
        val effects = buildEffects(dto.setFlags, dto.removeFlags, dto.modifyStats)
        val choiceId = dto.id?.takeIf { it.isNotBlank() }
            ?.let { ChoiceId(it) }
            ?: ChoiceId("${sceneId}_choice_$index")
        return Choice(
            id = choiceId,
            text = dto.text,
            nextScene = SceneId(dto.nextScene),
            condition = condition,
            effects = effects,
        )
    }

    /**
     * Flattens the three parallel JSON fields into a single ordered
     * [Effect] list. Order matches the current reader:
     * `set_flags` first, then `remove_flags`, then `modify_stats`.
     * `modify_stats` order follows the JSON object's key order
     * (`LinkedHashMap` in `kotlinx.serialization`).
     */
    private fun buildEffects(
        setFlags: List<String>,
        removeFlags: List<String>,
        modifyStats: Map<String, Int>,
    ): List<Effect> {
        val result = mutableListOf<Effect>()
        setFlags.forEach { result += Effect.SetFlag(it) }
        removeFlags.forEach { result += Effect.RemoveFlag(it) }
        modifyStats.forEach { (name, delta) -> result += Effect.ModifyStat(name, delta) }
        return result
    }

    // ---------- Path helpers ----------

    private fun chapterPath(index: Int, id: String): String =
        if (id.isBlank()) "chapters[$index]" else "chapters[$index:$id]"

    private fun scenePath(parentPath: String, index: Int, id: String): String =
        if (id.isBlank()) "$parentPath.scenes[$index]"
        else "$parentPath.scenes[$index:$id]"
}