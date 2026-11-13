package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory
import com.myapp.adventure.engine.ir.TagDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [TypeChecker].
 *
 * Covers the three DoD cases (`UnknownVariable`, `HasOnNonBool`, wrong
 * type for `initial_state`) and the extra effect checks adopted in
 * Ф2.2 (B2 decision, 2026-11-12): `SetFlagOnNonBool`,
 * `RemoveFlagOnNonBool`, `ModifyStatOnNonNumeric`, `CompareOnNonNumeric`,
 * `InRangeOnNonNumeric`.
 *
 * Also covers fallback mode (C4 decision, 2026-11-12): with an empty
 * `Adventure.variables`, types are inferred from `tags` + `initialState`,
 * and a typo like `has_swrod` still produces [TypeError.UnknownVariable].
 *
 * **What is NOT here.** `health >= "abc"` — that is a **parse** error,
 * caught by `ConditionParser` in Ф1.3.1 before the IR exists (see
 * `ConditionParserTest > non-integer value fails`). Ф2.2 does not
 * re-check parse-level errors.
 */
class TypeCheckerTest {

    // ---------- helpers ----------

    private fun variables(vararg pairs: Pair<String, VarType>): Map<String, VarDecl> =
        pairs.associate { (name, type) -> name to VarDecl(name, type) }

    private fun adventure(
        variables: Map<String, VarDecl> = emptyMap(),
        tags: List<TagDefinition> = emptyList(),
        initialState: InitialState = InitialState(),
        vararg scenes: Scene,
    ): Adventure {
        val start = scenes.firstOrNull()?.id ?: SceneId("start")
        return Adventure(
            title = "T",
            startScene = start,
            variables = variables,
            tags = tags,
            initialState = initialState,
            chapters = listOf(Chapter("ch", "ch", scenes.toList())),
        )
    }

    private fun scene(
        id: String,
        effects: List<Effect> = emptyList(),
        choices: List<Choice> = emptyList(),
    ): Scene = Scene(
        id = SceneId(id),
        text = "text of $id",
        effects = effects,
        choices = choices,
    )

    private fun choice(
        id: String = "c1",
        next: String = "start",
        condition: Condition? = null,
        effects: List<Effect> = emptyList(),
    ): Choice = Choice(
        id = ChoiceId(id),
        text = "go",
        nextScene = SceneId(next),
        condition = condition,
        effects = effects,
    )

    // ---------- empty input ----------

    @Test
    fun `empty adventure returns no errors`() {
        // No variables, no tags, no initial state -> type table empty ->
        // nothing to check against. The "you have not declared variables"
        // signal is a UI concern, not a TypeError (see TypeChecker KDoc).
        val adv = Adventure(
            title = "T",
            startScene = SceneId("start"),
            chapters = listOf(Chapter("ch", "ch", listOf(Scene(SceneId("start"), "text")))),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    // ---------- strict mode: unknown variable ----------

    @Test
    fun `strict mode - unknown variable in condition`() {
        val adv = adventure(
            variables = variables("sword" to VarType.ItemCount),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("never_declared")))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(1, errors.size)
        assertIs<TypeError.UnknownVariable>(errors.single())
        assertEquals("never_declared", (errors.single() as TypeError.UnknownVariable).name)
        assertEquals(SceneId("start"), errors.single().sceneId)
    }

    @Test
    fun `strict mode - unknown variable in effect`() {
        val adv = adventure(
            variables = variables("gold" to VarType.IntMin(0)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("never_declared", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(1, errors.size)
        assertIs<TypeError.UnknownVariable>(errors.single())
    }

    // ---------- strict mode: HasOnNonBool ----------

    @Test
    fun `strict mode - has on ItemCount is HasOnNonBool`() {
        val adv = adventure(
            variables = variables("sword" to VarType.ItemCount),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("sword")))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(1, errors.size)
        assertIs<TypeError.HasOnNonBool>(errors.single())
        assertEquals("sword", (errors.single() as TypeError.HasOnNonBool).name)
    }

    @Test
    fun `strict mode - has on IntRange is HasOnNonBool`() {
        val adv = adventure(
            variables = variables("health" to VarType.IntRange(0, 100)),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("health")))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.HasOnNonBool>(errors.single())
    }

    // ---------- strict mode: effect type checks (B2) ----------

    @Test
    fun `strict mode - set_flag on numeric is SetFlagOnNonBool`() {
        val adv = adventure(
            variables = variables("gold" to VarType.IntMin(0)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.SetFlag("gold"))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.SetFlagOnNonBool>(errors.single())
    }

    @Test
    fun `strict mode - remove_flag on Bool-free numeric is RemoveFlagOnNonBool`() {
        val adv = adventure(
            variables = variables("score" to VarType.Integer),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.RemoveFlag("score"))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.RemoveFlagOnNonBool>(errors.single())
    }

    @Test
    fun `strict mode - modify_stat on Bool is ModifyStatOnNonNumeric`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("has_key", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.ModifyStatOnNonNumeric>(errors.single())
    }

    @Test
    fun `strict mode - compare on Bool is CompareOnNonNumeric`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(
                        choice(condition = Condition.Compare("has_key", CompareOp.GT, 0)),
                    ),
                ),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.CompareOnNonNumeric>(errors.single())
    }

    @Test
    fun `strict mode - in_range on Bool is InRangeOnNonNumeric`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(
                        choice(condition = Condition.InRange("has_key", 0, 1)),
                    ),
                ),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.InRangeOnNonNumeric>(errors.single())
    }

    // ---------- strict mode: initial state checks (B2) ----------

    @Test
    fun `strict mode - initial flag on numeric is InitialFlagOnNonBool`() {
        val adv = adventure(
            variables = variables("health" to VarType.IntRange(0, 100)),
            initialState = InitialState(flags = listOf("health")),
            scenes = arrayOf(scene("start")),
        )
        val errors = TypeChecker.check(adv)
        val e = errors.single()
        assertIs<TypeError.InitialFlagOnNonBool>(e)
        assertEquals(null, e.sceneId)
    }

    @Test
    fun `strict mode - initial stat on Bool is InitialStatOnNonNumeric`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            initialState = InitialState(stats = mapOf("has_key" to 1)),
            scenes = arrayOf(scene("start")),
        )
        val errors = TypeChecker.check(adv)
        val e = errors.single()
        assertIs<TypeError.InitialStatOnNonNumeric>(e)
        assertEquals(null, e.sceneId)
    }

    // ---------- strict mode: clean cases ----------

    @Test
    fun `strict mode - clean adventure returns empty list`() {
        val adv = adventure(
            variables = variables(
                "sword" to VarType.ItemCount,
                "health" to VarType.IntRange(0, 100),
                "has_key" to VarType.Bool,
            ),
            initialState = InitialState(
                flags = listOf("has_key"),
                stats = mapOf("sword" to 1, "health" to 100),
            ),
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(
                        choice(
                            condition = Condition.Has("has_key"),
                            effects = listOf(Effect.ModifyStat("health", -10)),
                        ),
                        choice(
                            id = "c2",
                            condition = Condition.Compare("sword", CompareOp.GT, 0),
                            effects = listOf(Effect.ModifyStat("sword", -1)),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    // ---------- strict mode: variables wins over tags ----------

    @Test
    fun `strict mode - variable in tags but not in variables is UnknownVariable`() {
        // `variables` is non-empty -> strict mode. `tags` is ignored for
        // type inference; the only source of truth is `variables`. So a
        // name that is in `tags` but not in `variables` is unknown, not
        // "found via tag".
        val adv = adventure(
            variables = variables("gold" to VarType.IntMin(0)),
            tags = listOf(TagDefinition("sword", category = TagCategory.ITEM)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("sword", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.UnknownVariable>(errors.single())
        assertEquals("sword", (errors.single() as TypeError.UnknownVariable).name)
    }

    // ---------- fallback mode (empty variables) ----------

    @Test
    fun `fallback - typo in has flag produces UnknownVariable`() {
        // No variables; `sword` is a tag (ITEM). Condition uses `swrod` —
        // a typo. In fallback mode `sword` is inferred as ItemCount, but
        // `swrod` is not in the inferred table at all, so it is
        // UnknownVariable.
        //
        // This is the DoD requirement ("опечатка `has_swrod` → ошибка").
        val adv = adventure(
            tags = listOf(TagDefinition("sword", category = TagCategory.ITEM)),
            initialState = InitialState(stats = mapOf("sword" to 1)),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("swrod")))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(1, errors.size)
        assertIs<TypeError.UnknownVariable>(errors.single())
        assertEquals("swrod", (errors.single() as TypeError.UnknownVariable).name)
    }

    @Test
    fun `fallback - has on item tag produces HasOnNonBool`() {
        // ITEM -> ItemCount. `has_sword` on ItemCount is the same mistake
        // as in strict mode.
        val adv = adventure(
            tags = listOf(TagDefinition("sword", category = TagCategory.ITEM)),
            initialState = InitialState(stats = mapOf("sword" to 1)),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("sword")))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(1, errors.size)
        assertIs<TypeError.HasOnNonBool>(errors.single())
    }

    @Test
    fun `fallback - journal in initial flags treated as Bool`() {
        // No tags, but `started` is in `initialState.flags` -> Bool.
        val adv = adventure(
            initialState = InitialState(flags = listOf("started")),
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("started")))),
            ),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    @Test
    fun `fallback - health tag treated as IntMin, modify_stat allowed`() {
        val adv = adventure(
            tags = listOf(TagDefinition("health", category = TagCategory.HEALTH)),
            initialState = InitialState(stats = mapOf("health" to 100)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("health", -10))),
            ),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    @Test
    fun `fallback - item tag treated as ItemCount, modify_stat allowed`() {
        val adv = adventure(
            tags = listOf(TagDefinition("potion", category = TagCategory.ITEM)),
            initialState = InitialState(stats = mapOf("potion" to 3)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("potion", -1))),
            ),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    // ---------- sorting ----------

    @Test
    fun `initial state errors come before scene errors`() {
        // Both errors are present; InitialState errors (sceneId = null)
        // must come first in the sorted output.
        val adv = adventure(
            variables = variables("health" to VarType.Bool),
            initialState = InitialState(stats = mapOf("health" to 100)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("never", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(2, errors.size)
        assertIs<TypeError.InitialStatOnNonNumeric>(errors[0])
        assertIs<TypeError.UnknownVariable>(errors[1])
    }

    @Test
    fun `errors are sorted by scene id then by message`() {
        val adv = adventure(
            variables = variables("gold" to VarType.IntMin(0)),
            scenes = arrayOf(
                scene("z_scene", effects = listOf(Effect.ModifyStat("unknown_z", 1))),
                scene("a_scene", effects = listOf(Effect.ModifyStat("unknown_a", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(2, errors.size)
        assertEquals("a_scene", errors[0].sceneId?.value)
        assertEquals("z_scene", errors[1].sceneId?.value)
    }

    @Test
    fun `multiple errors in one scene are all reported`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(
                        choice(
                            id = "c1",
                            effects = listOf(
                                Effect.ModifyStat("has_key", 1),          // Bool used as numeric
                                Effect.SetFlag("unknown_flag"),           // unknown
                            ),
                        ),
                    ),
                ),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertEquals(2, errors.size)
        assertIs<TypeError.ModifyStatOnNonNumeric>(errors[0])
        assertIs<TypeError.UnknownVariable>(errors[1])
    }

    // ---------- integration: test_adventure.json is type-clean ----------

    @Test
    fun `real test_adventure_json is type-clean`() {
        // Sanity check before Ф2.3 wires TypeChecker into JsonToIr.parse:
        // the reference sample must pass the type checker. If it does not,
        // either the sample is broken (fix test_adventure.json) or the
        // fallback mode (C4) is too strict (fix TypeChecker). Do not wire
        // the checker into parse until this is green.
        //
        // After Ф2.3, this test will be replaced by a stronger one:
        // "JsonToIr.parse returns Success for test_adventure.json" — the
        // same guarantee, expressed at the pipeline level instead of
        // manually invoking TypeChecker.check. The current form is what
        // makes the wiring safe to do in the first place.
        val stream = javaClass.getResourceAsStream("/test_adventure.json")
            ?: error("test_adventure.json not on classpath")
        val raw = stream.bufferedReader().use { it.readText() }
        val parseResult = com.myapp.adventure.engine.format.JsonToIr.parse(raw)
        val adventure = when (parseResult) {
            is com.myapp.adventure.engine.parse.ParseResult.Success -> parseResult.value
            is com.myapp.adventure.engine.parse.ParseResult.Failure ->
                error("test_adventure.json failed to parse: ${parseResult.errors}")
        }
        val typeErrors = TypeChecker.check(adventure)
        assertEquals(
            emptyList(),
            typeErrors,
            "test_adventure.json must be type-clean under current rules. " +
                    "Either the sample is broken (fix test_adventure.json) " +
                    "or TypeChecker fallback mode is too strict (fix TypeChecker).",
        )
    }

    // ---------- Ф3.1: Effect.Set ----------

    @Test
    fun `strict mode - set on Bool is ModifyStatOnNonNumeric`() {
        val adv = adventure(
            variables = variables("has_key" to VarType.Bool),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.Set("has_key", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.ModifyStatOnNonNumeric>(errors.single())
    }

    @Test
    fun `strict mode - set on numeric is allowed`() {
        val adv = adventure(
            variables = variables("health" to VarType.IntRange(0, 100)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.Set("health", 100))),
            ),
        )
        assertEquals(emptyList(), TypeChecker.check(adv))
    }

    @Test
    fun `strict mode - set unknown variable is UnknownVariable`() {
        val adv = adventure(
            variables = variables("health" to VarType.IntRange(0, 100)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.Set("never_declared", 1))),
            ),
        )
        val errors = TypeChecker.check(adv)
        assertIs<TypeError.UnknownVariable>(errors.single())
    }

}