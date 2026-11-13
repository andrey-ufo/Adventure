package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.format.JsonToIr
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
import com.myapp.adventure.engine.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [inferTypes].
 *
 * Covers the priority order documented on [inferTypes]:
 * `HEALTH tag > ITEM tag > numeric usage > Bool usage`.
 *
 * Also verifies the DoD-requirement that on `test_adventure.json` (which
 * since Ф2.8.1 carries an explicit `variables` block) `inferTypes` produces
 * **exactly** the same map the author wrote by hand. If inference drifts
 * from manual declaration, either the file's `variables` is wrong (fix the
 * file) or the inference logic is wrong (fix the code). The test is the
 * tripwire.
 */
class TypeInferenceTest {

    // ---------- helpers ----------

    private fun adventure(
        tags: List<TagDefinition> = emptyList(),
        initialState: InitialState = InitialState(),
        vararg scenes: Scene,
    ): Adventure = Adventure(
        title = "T",
        startScene = scenes.firstOrNull()?.id ?: SceneId("start"),
        tags = tags,
        initialState = initialState,
        chapters = listOf(Chapter("ch", "ch", scenes.toList())),
    )

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
        condition: Condition? = null,
        effects: List<Effect> = emptyList(),
    ): Choice = Choice(
        id = ChoiceId("c"),
        text = "go",
        nextScene = SceneId("start"),
        condition = condition,
        effects = effects,
    )

    // ---------- initial state signals ----------

    @Test
    fun `initial flag infers Bool`() {
        val adv = adventure(
            initialState = InitialState(flags = listOf("started")),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["started"]?.type)
    }

    @Test
    fun `initial stat infers IntMin zero`() {
        val adv = adventure(
            initialState = InitialState(stats = mapOf("gold" to 10)),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["gold"]?.type)
    }

    // ---------- tag signals ----------

    @Test
    fun `HEALTH tag infers IntMin zero`() {
        val adv = adventure(
            tags = listOf(TagDefinition("health", category = TagCategory.HEALTH)),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["health"]?.type)
    }

    @Test
    fun `ITEM tag infers ItemCount`() {
        val adv = adventure(
            tags = listOf(TagDefinition("sword", category = TagCategory.ITEM)),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.ItemCount, inferTypes(adv)["sword"]?.type)
    }

    @Test
    fun `JOURNAL tag infers Bool when not in stats`() {
        val adv = adventure(
            tags = listOf(TagDefinition("seen_dragon", category = TagCategory.JOURNAL)),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["seen_dragon"]?.type)
    }

    @Test
    fun `JOURNAL tag infers IntMin when in stats`() {
        val adv = adventure(
            tags = listOf(TagDefinition("quest_steps", category = TagCategory.JOURNAL)),
            initialState = InitialState(stats = mapOf("quest_steps" to 0)),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["quest_steps"]?.type)
    }

    // ---------- usage signals ----------

    @Test
    fun `SetFlag effect infers Bool`() {
        val adv = adventure(
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.SetFlag("found_key"))),
            ),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["found_key"]?.type)
    }

    @Test
    fun `RemoveFlag effect infers Bool`() {
        val adv = adventure(
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.RemoveFlag("found_key"))),
            ),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["found_key"]?.type)
    }

    @Test
    fun `Has condition infers Bool`() {
        val adv = adventure(
            scenes = arrayOf(
                scene("start", choices = listOf(choice(condition = Condition.Has("sword")))),
            ),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["sword"]?.type)
    }

    @Test
    fun `ModifyStat effect infers IntMin zero`() {
        val adv = adventure(
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("health", -10))),
            ),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["health"]?.type)
    }

    @Test
    fun `Compare condition infers IntMin zero`() {
        val adv = adventure(
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(choice(condition = Condition.Compare("health", CompareOp.GE, 50))),
                ),
            ),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["health"]?.type)
    }

    @Test
    fun `InRange condition infers IntMin zero`() {
        val adv = adventure(
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(choice(condition = Condition.InRange("health", 0, 100))),
                ),
            ),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["health"]?.type)
    }

    @Test
    fun `Not condition recurses into inner`() {
        val adv = adventure(
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(choice(condition = Condition.Not(Condition.Has("sword")))),
                ),
            ),
        )
        assertEquals(VarType.Bool, inferTypes(adv)["sword"]?.type)
    }

    @Test
    fun `And condition recurses into both sides`() {
        val adv = adventure(
            scenes = arrayOf(
                scene(
                    "start",
                    choices = listOf(
                        choice(
                            condition = Condition.And(
                                Condition.Has("has_key"),
                                Condition.Compare("health", CompareOp.GT, 0),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val types = inferTypes(adv)
        assertEquals(VarType.Bool, types["has_key"]?.type)
        assertEquals(VarType.IntMin(0), types["health"]?.type)
    }

    // ---------- priority resolution ----------

    @Test
    fun `HEALTH tag wins over Bool usage`() {
        // Contradictory: author declared HEALTH but uses set_flag. Inference
        // prioritises the explicit category — the type checker will report
        // SetFlagOnNonBool on the usage, which is the correct signal for the
        // author to fix one or the other.
        val adv = adventure(
            tags = listOf(TagDefinition("health", category = TagCategory.HEALTH)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.SetFlag("health"))),
            ),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["health"]?.type)
    }

    @Test
    fun `ITEM tag wins over numeric usage`() {
        // ITEM is ItemCount; ModifyStat is numeric. Both consistent, but
        // ITEM is more specific and expresses the author's intent more
        // clearly. ItemCount still supports ModifyStat.
        val adv = adventure(
            tags = listOf(TagDefinition("potion", category = TagCategory.ITEM)),
            scenes = arrayOf(
                scene("start", effects = listOf(Effect.ModifyStat("potion", -1))),
            ),
        )
        assertEquals(VarType.ItemCount, inferTypes(adv)["potion"]?.type)
    }

    @Test
    fun `numeric usage wins over Bool usage`() {
        // Contradictory: same name set as a flag and modified as a stat.
        // Numeric wins (see inferTypes KDoc — a numeric can appear in
        // set_flag only by mistake, while set_flag is the only way to
        // toggle a Bool). The type checker then reports the set_flag as
        // wrong — a clear signal for the author.
        val adv = adventure(
            scenes = arrayOf(
                scene(
                    "start",
                    effects = listOf(
                        Effect.SetFlag("count"),
                        Effect.ModifyStat("count", 1),
                    ),
                ),
            ),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["count"]?.type)
    }

    // ---------- edge cases ----------

    @Test
    fun `empty adventure returns empty map`() {
        val adv = adventure(scenes = arrayOf(scene("start")))
        assertEquals(emptyMap(), inferTypes(adv))
    }

    @Test
    fun `name in both flags and stats - numeric wins`() {
        // Pathological: same name in both initial-state collections. The
        // type checker will report InitialFlagOnNonBool or
        // InitialStatOnNonNumeric (depending on what the inferred type
        // ends up being); inference just needs to pick one deterministically.
        // Numeric wins by priority order — that matches the type checker's
        // expectation that ModifyStat / Compare on the name should work.
        val adv = adventure(
            initialState = InitialState(
                flags = listOf("ambiguous"),
                stats = mapOf("ambiguous" to 1),
            ),
            scenes = arrayOf(scene("start")),
        )
        assertEquals(VarType.IntMin(0), inferTypes(adv)["ambiguous"]?.type)
    }

    @Test
    fun `VarDecl name matches map key`() {
        val adv = adventure(
            initialState = InitialState(flags = listOf("started")),
            scenes = arrayOf(scene("start")),
        )
        val decl = inferTypes(adv)["started"]
        assertEquals("started", decl?.name)
    }

    // ---------- integration: test_adventure.json ----------

    @Test
    fun `real test_adventure_json - inference matches declared variables`() {
        // DoD-close: the reference sample now declares its types by hand
        // (added in Ф2.8.1). `inferTypes` must produce exactly the same
        // map — otherwise either the inference logic or the sample is
        // wrong. This test is the tripwire.
        val stream = javaClass.getResourceAsStream("/test_adventure.json")
            ?: error("test_adventure.json not on classpath")
        val raw = stream.bufferedReader().use { it.readText() }
        val adventure = assertIs<ParseResult.Success<Adventure>>(
            JsonToIr.parse(raw),
            "test_adventure.json must parse successfully",
        ).value

        // Precondition: the sample carries an explicit variables block.
        // If someone removes it (breaks 2.8.1), we want a clear message
        // here, not a confusing diff later.
        assertEquals(
            11,
            adventure.variables.size,
            "test_adventure.json should declare exactly 11 variables (see 2.8.1)",
        )

        val inferred = inferTypes(adventure)
        assertEquals(
            adventure.variables,
            inferred,
            "inferTypes must reproduce the sample's manual declarations. " +
                    "If they differ, either the inference logic drifted " +
                    "(fix TypeInference.kt) or the sample was edited " +
                    "inconsistently (fix test_adventure.json).",
        )
    }
}