package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.parse.ParseError
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.engine.ir.ChoiceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonToIrTest {

    private fun success(json: String): Adventure {
        val result = JsonToIr.parse(json)
        val s = assertIs<ParseResult.Success<Adventure>>(
            result,
            "expected Success, got $result",
        )
        return s.value
    }

    private fun failure(json: String): List<ParseError> {
        val result = JsonToIr.parse(json)
        val f = assertIs<ParseResult.Failure>(
            result,
            "expected Failure, got $result",
        )
        assertTrue(f.errors.isNotEmpty(), "Failure must contain at least one error")
        return f.errors
    }

    @Test
    fun `minimal valid adventure`() {
        val json = """
            {
              "title": "Test",
              "chapters": [{
                "id": "c1",
                "scenes": [{"id": "s1", "text": "Hello"}]
              }]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals("Test", adv.title)
        assertEquals(5, adv.version) // CURRENT_VERSION after Ф1.7.0.4
        assertEquals(1, adv.chapters.size)
        assertEquals("c1", adv.chapters[0].id)
        assertEquals("s1", adv.chapters[0].scenes[0].id.value)
        assertNull(adv.deathScene)
    }

    @Test
    fun `version absent becomes CURRENT_VERSION`() {
        val json = """{"title":"T","chapters":[{"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}]}"""
        assertEquals(Adventure.CURRENT_VERSION, success(json).version)
    }

    @Test
    fun `version present is preserved`() {
        val json = """{"version":2,"title":"T","chapters":[{"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}]}"""
        assertEquals(2, success(json).version)
    }

    @Test
    fun `empty chapters is an error`() {
        val json = """{"title":"T","chapters":[]}"""
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("no chapters") },
            "expected 'no chapters' Structure error, got $errors",
        )
    }

    @Test
    fun `empty chapter scenes is an error`() {
        val json = """{"title":"T","chapters":[{"id":"c1","scenes":[]}]}"""
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("no scenes") },
            "expected 'no scenes' Structure error, got $errors",
        )
    }

    @Test
    fun `blank scene id is an error`() {
        val json = """{"title":"T","chapters":[{"id":"c1","scenes":[{"id":"  ","text":"Hi"}]}]}"""
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("Scene id") },
            "expected 'Scene id' Structure error, got $errors",
        )
    }

    @Test
    fun `blank choice text is an error`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"","next_scene":"s1"}]
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("Choice text") },
            "expected 'Choice text' Structure error, got $errors",
        )
    }

    @Test
    fun `blank choice next_scene is an error`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"Go","next_scene":""}]
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("next_scene") },
            "expected 'next_scene' Structure error, got $errors",
        )
    }

    @Test
    fun `invalid condition is a ConditionError with path`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"Go","next_scene":"s1","condition":"has_sword && health"}]
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        val condErr = errors.filterIsInstance<ParseError.ConditionError>().single()
        val path = condErr.path
        assertTrue(
            path != null && path.contains("choices[0].condition"),
            "expected path to contain 'choices[0].condition', got $path",
        )
        assertTrue(
            path.contains("c1") && path.contains("s1"),
            "expected path to contain chapter id 'c1' and scene id 's1', got $path",
        )
    }

    @Test
    fun `multiple errors accumulated`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"","text":"Hi","choices":[{"text":"","next_scene":"s1"}]
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        // blank scene.id + blank choice.text = at least 2
        assertTrue(
            errors.size >= 2,
            "expected at least 2 accumulated errors, got ${errors.size}: $errors",
        )
    }

    @Test
    fun `effects order set remove modify`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi",
              "set_flags":["a","b"],
              "remove_flags":["c"],
              "modify_stats":{"health":-5,"mana":3}
            }]}]}
        """.trimIndent()
        val adv = success(json)
        val effects = adv.chapters[0].scenes[0].effects
        assertEquals(5, effects.size)
        assertIs<Effect.SetFlag>(effects[0])
        assertIs<Effect.SetFlag>(effects[1])
        assertIs<Effect.RemoveFlag>(effects[2])
        assertIs<Effect.ModifyStat>(effects[3])
        assertIs<Effect.ModifyStat>(effects[4])
        assertEquals("health", (effects[3] as Effect.ModifyStat).name)
        assertEquals(-5, (effects[3] as Effect.ModifyStat).delta)
        assertEquals("mana", (effects[4] as Effect.ModifyStat).name)
        assertEquals(3, (effects[4] as Effect.ModifyStat).delta)
    }

    @Test
    fun `malformed JSON is a Syntax error`() {
        val json = """{"title":"""
        val errors = failure(json)
        val syntax = errors.single()
        assertIs<ParseError.Syntax>(syntax)
    }

    @Test
    fun `typo in flag name produces TypeError now that F2_3 wires the checker`() {
        // Ф1.3.4.1 documented this boundary as "parser accepts, type
        // checker rejects". Ф2.3 now actually runs the type checker inside
        // `JsonToIr.parse`, so the same JSON that used to return
        // `Success(ir)` now returns `Failure(typeErrors = [UnknownVariable])`.
        //
        // **Fixture note.** The JSON includes `initial_state.flags` so
        // that TypeChecker's fallback mode (C4) builds a non-empty type
        // table. With an entirely empty `tags` + `initialState`,
        // TypeChecker returns `emptyList()` (nothing to check against —
        // see its KDoc, "Fallback" mode), and `swrod` would slip through
        // as `Success`. The flag `marker` itself is irrelevant; it just
        // makes the table non-empty, so `swrod` (which is not in it) is
        // correctly reported as UnknownVariable.
        //
        // The parser/type-checker boundary is still preserved: this error
        // is a **type error**, not a **parse error** — it lives in
        // `typeErrors`, not in `errors`. Callers that only inspect
        // `result.errors` see "the JSON is structurally valid"; callers
        // that also inspect `result.typeErrors` see "but `swrod` is not a
        // declared variable".
        val json = """
            {"title":"T","initial_state":{"flags":["marker"]},"chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"Go","next_scene":"s1","condition":"has_swrod"}]
            }]}]}
        """.trimIndent()
        val result = JsonToIr.parse(json)
        val failure = assertIs<ParseResult.Failure>(result)
        assertEquals(emptyList(), failure.errors)
        assertEquals(1, failure.typeErrors.size)
        val typeErr = assertIs<com.myapp.adventure.engine.ir.types.TypeError.UnknownVariable>(
            failure.typeErrors.single(),
        )
        assertEquals("swrod", typeErr.name)
    }

    @Test
    fun `type errors are not checked when parse errors are present`() {
        // B1 decision (Ф2.3): if parsing fails, type checking is not
        // attempted. A partial IR would produce false negatives at best
        // (unchecked conditions) and false positives at worst (unknown
        // variables that would have been declared in the unparsed part).
        //
        // The input below combines two independent errors:
        // - a **parse error**: blank choice.text (structural problem);
        // - a **type error**: `has_swrod` (unknown variable).
        // Expected: `Failure` with `errors` non-empty (the parse error)
        // and `typeErrors` empty (typecheck was skipped).
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[
                {"text":"","next_scene":"s1","condition":"has_swrod"}
              ]
            }]}]}
        """.trimIndent()
        val result = JsonToIr.parse(json)
        val failure = assertIs<ParseResult.Failure>(result)
        assertTrue(
            failure.errors.isNotEmpty(),
            "expected parse errors (blank choice.text), got ${failure.errors}",
        )
        assertEquals(
            emptyList(),
            failure.typeErrors,
            "type checking must not run when parsing failed (B1 decision)",
        )
    }

    @Test
    fun `type errors produced when parse is clean - HasOnNonBool path`() {
        // Companion to the test above, but exercises a **different**
        // TypeError variant through the same pipeline: not "unknown
        // variable", but "variable exists, but `has_` on it is wrong
        // because its type is not Bool".
        //
        // Fixture: `sword` is declared as an ITEM tag (fallback mode
        // infers `VarType.ItemCount`) and given `1` in the initial state.
        // The choice uses `has_sword` on it — the common authoring
        // mistake that Ф1.7.1a-fix3 fixed in `test_adventure.json` by
        // rewriting it as `sword > 0`. TypeChecker now catches this
        // automatically. The expected error is `HasOnNonBool`, with a
        // fix-hint in its message ("use 'sword > 0' instead").
        val json = """
            {
              "title": "T",
              "tags": [{"name": "sword", "category": "ITEM"}],
              "initial_state": {"stats": {"sword": 1}},
              "chapters": [{"id": "c1", "scenes": [{
                "id": "s1",
                "text": "Hi",
                "choices": [{"text": "Go", "next_scene": "s1", "condition": "has_sword"}]
              }]}]
            }
        """.trimIndent()
        val result = JsonToIr.parse(json)
        val failure = assertIs<ParseResult.Failure>(result)
        assertEquals(emptyList(), failure.errors)
        assertEquals(1, failure.typeErrors.size)
        val typeErr = assertIs<com.myapp.adventure.engine.ir.types.TypeError.HasOnNonBool>(
            failure.typeErrors.single(),
        )
        assertEquals("sword", typeErr.name)
    }

    @Test
    fun `choice id generated when absent in JSON`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"start","text":"Hi","choices":[
                {"text":"First","next_scene":"start"},
                {"text":"Second","next_scene":"start"}
              ]
            }]}]}
        """.trimIndent()
        val adv = success(json)
        val choices = adv.chapters[0].scenes[0].choices
        assertEquals(ChoiceId("start_choice_0"), choices[0].id)
        assertEquals(ChoiceId("start_choice_1"), choices[1].id)
    }

    @Test
    fun `choice id from JSON preserved when present`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"start","text":"Hi","choices":[
                {"id":"my_custom_id","text":"Go","next_scene":"start"}
              ]
            }]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(ChoiceId("my_custom_id"), adv.chapters[0].scenes[0].choices[0].id)
    }

    @Test
    fun `scene gate absent is null`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi"
            }]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(null, adv.chapters[0].scenes[0].gate)
    }

    @Test
    fun `scene gate positive is parsed`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","gate":100
            }]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(100, adv.chapters[0].scenes[0].gate?.requiredSteps)
    }

    @Test
    fun `scene gate zero is an error`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","gate":0
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("gate must be") },
            "expected 'gate must be' Structure error, got $errors",
        )
    }

    @Test
    fun `scene gate negative is an error`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","gate":-5
            }]}]}
        """.trimIndent()
        val errors = failure(json)
        assertTrue(
            errors.any { it is ParseError.Structure && it.message.contains("gate must be") },
            "expected 'gate must be' Structure error, got $errors",
        )
    }

    @Test
    fun `v1 file migrates chapter required_steps to gate on previous chapter last scene`() {
        // v1 file: no explicit "version", gates on chapters.
        val json = """
            {"title":"T","chapters":[
              {"id":"c1","required_steps":0,"scenes":[
                {"id":"s1","text":"A"},{"id":"s2","text":"B"}
              ]},
              {"id":"c2","required_steps":100,"scenes":[
                {"id":"s3","text":"C"}
              ]}
            ]}
        """.trimIndent()
        val adv = success(json)
        // s2 is the last scene of c1; the gate from c2.required_steps migrates to it.
        assertEquals(100, adv.chapters[0].scenes[1].gate?.requiredSteps)
        // No gate on s1 or s3.
        assertEquals(null, adv.chapters[0].scenes[0].gate)
        assertEquals(null, adv.chapters[1].scenes[0].gate)
    }

    @Test
    fun `v1 file explicit version 1 migrates the same way`() {
        val json = """
            {"version":1,"title":"T","chapters":[
              {"id":"c1","required_steps":0,"scenes":[{"id":"s1","text":"A"}]},
              {"id":"c2","required_steps":42,"scenes":[{"id":"s2","text":"B"}]}
            ]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(42, adv.chapters[0].scenes[0].gate?.requiredSteps)
    }


    @Test
    fun `v2 file without start_scene migrates from chapters`() {
        val json = """
            {"version":2,"title":"T","chapters":[
              {"id":"c1","scenes":[{"id":"first","text":"A"},{"id":"second","text":"B"}]},
              {"id":"c2","scenes":[{"id":"third","text":"C"}]}
            ]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(SceneId("first"), adv.startScene)
    }

    @Test
    fun `v2 file with explicit start_scene preserves it`() {
        val json = """
            {"version":2,"title":"T","start_scene":"second","chapters":[
              {"id":"c1","scenes":[{"id":"first","text":"A"},{"id":"second","text":"B"}]}
            ]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(SceneId("second"), adv.startScene)
    }

    @Test
    fun `gate_repeatable absent defaults to false`() {
        // Identity migration v4 → v5: files that predate gate_repeatable
        // get repeatable = false ("once passed, stays passed in this
        // playthrough"). This is the common "unlock the next chapter"
        // case — the least surprising default.
        val json = """
            {"version":5,"title":"T","start_scene":"s1","chapters":[{"id":"c1","scenes":[
              {"id":"s1","text":"Hi","gate":100}
            ]}]}
        """.trimIndent()
        val adv = success(json)
        val gate = adv.chapters[0].scenes[0].gate
        assertEquals(100, gate?.requiredSteps)
        assertEquals(false, gate?.repeatable)
    }

    @Test
    fun `gate_repeatable true is parsed`() {
        // Per-visit mode: the gate re-engages every time the player
        // enters this scene. Used by authors for "travelled a path" /
        // "wait N steps before each visit" scenarios.
        val json = """
            {"version":5,"title":"T","start_scene":"s1","chapters":[{"id":"c1","scenes":[
              {"id":"s1","text":"Hi","gate":4000,"gate_repeatable":true}
            ]}]}
        """.trimIndent()
        val adv = success(json)
        val gate = adv.chapters[0].scenes[0].gate
        assertEquals(4000, gate?.requiredSteps)
        assertEquals(true, gate?.repeatable)
    }

    @Test
    fun `unknown next_scene parses as SceneId - reachability is F2`() {
        // `next_scene: "nonexistent"` parses successfully: the parser
        // does not know the full scene set while parsing one choice
        // (the scene may be defined in another chapter). Reachability is
        // the analyzer's job (Ф1.6 / Ф2); the resulting IR contains
        // SceneId("nonexistent"), which the analyzer will flag.
        //
        // This test documents the boundary. If it starts failing, someone
        // moved reachability into the parser.
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"Go","next_scene":"nonexistent"}]
            }]}]}
        """.trimIndent()
        val adv = success(json)
        val choice = adv.chapters[0].scenes[0].choices[0]
        assertEquals(SceneId("nonexistent"), choice.nextScene)
    }

    @Test
    fun `v4 file without is_ending defaults to false`() {
        // Identity migration v3 → v4: files that predate the field have
        // no is_ending. The parser must map absent → false, not crash
        // and not leave it null.
        // start_scene is required since v3; the parser rejects v3+ files
        // without it before is_ending is even looked at — so we include it.
        val json = """
            {"version":4,"title":"T","start_scene":"s1","chapters":[{"id":"c1","scenes":[
              {"id":"s1","text":"Hi"}
            ]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(false, adv.chapters[0].scenes[0].isEnding)
    }

    @Test
    fun `v4 file with is_ending true marks the scene as ending`() {
        // Multiple endings are allowed by design (see Scene.isEnding KDoc).
        // Here: `mid` has a choice, `end1` and `end2` are both endings.
        // start_scene is required since v3; without it the parser returns
        // ParseError.Structure before any is_ending logic runs.
        val json = """
            {"version":4,"title":"T","start_scene":"mid","chapters":[{"id":"c1","scenes":[
              {"id":"mid","text":"Choose","choices":[
                {"text":"Win","next_scene":"end1"},
                {"text":"Retire","next_scene":"end2"}
              ]},
              {"id":"end1","text":"You win","is_ending":true},
              {"id":"end2","text":"You retire","is_ending":true}
            ]}]}
        """.trimIndent()
        val adv = success(json)
        val scenes = adv.chapters[0].scenes
        assertEquals(3, scenes.size)
        assertEquals(false, scenes[0].isEnding) // mid
        assertEquals(true, scenes[1].isEnding)  // end1
        assertEquals(true, scenes[2].isEnding)  // end2
    }

    @Test
    fun `v3 file without is_ending migrates to false`() {
        // A v3 file has no is_ending field. After the v3 → v4 identity
        // migration, Scene.isEnding must be false, not null. This is the
        // last layer of the "old files keep working" guarantee at the
        // parser level — the analyzer layer (DeadEnd ERROR on
        // unreachable/no-choices scenes) is what surfaces the change.
        val json = """
            {"version":3,"title":"T","start_scene":"s1","chapters":[
              {"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}
            ]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(false, adv.chapters[0].scenes[0].isEnding)
    }

    // ---------- Ф2.8: variables parsing ----------

    @Test
    fun `variables block parses into VarDecl map`() {
        val json = """
            {
              "title": "T",
              "variables": {
                "health": {"type": "int_range", "min": 0, "max": 100},
                "sword": {"type": "item_count"},
                "has_key": {"type": "bool"}
              },
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(3, adv.variables.size)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.IntRange(0, 100),
            adv.variables["health"]?.type,
        )
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.ItemCount,
            adv.variables["sword"]?.type,
        )
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.Bool,
            adv.variables["has_key"]?.type,
        )
        // VarDecl.name comes from the map key, not from the value.
        assertEquals("health", adv.variables["health"]?.name)
    }

    @Test
    fun `variables absent falls back to empty map`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(emptyMap(), adv.variables)
    }

    @Test
    fun `unknown variable type downgrades to Integer`() {
        val json = """
            {
              "title": "T",
              "variables": {"foo": {"type": "future_type", "min": 5}},
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.Integer,
            adv.variables["foo"]?.type,
        )
    }

    @Test
    fun `int_min without min downgrades to Integer`() {
        // Matches :app EditorVarDecl.toVarType() behaviour (Ф2.7).
        val json = """
            {
              "title": "T",
              "variables": {"gold": {"type": "int_min"}},
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.Integer,
            adv.variables["gold"]?.type,
        )
    }

    @Test
    fun `int_range without max downgrades to Integer`() {
        val json = """
            {
              "title": "T",
              "variables": {"health": {"type": "int_range", "min": 0}},
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.Integer,
            adv.variables["health"]?.type,
        )
    }

    // ---------- Ф3.5: invariants ----------

    @Test
    fun `invariants parse into IR`() {
        val json = """
            {
              "title": "T",
              "invariants": [
                {"condition": "health <= 0", "outcome": "death"},
                {"condition": "stamina <= 0", "outcome": "death"}
              ],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(2, adv.invariants.size)
        assertEquals(
            com.myapp.adventure.engine.ir.CompareOp.LE,
            (adv.invariants[0].condition as com.myapp.adventure.engine.ir.Condition.Compare).op,
        )
        assertEquals(
            com.myapp.adventure.engine.ir.Outcome.Death,
            adv.invariants[0].outcome,
        )
    }

    @Test
    fun `invariants absent falls back to empty list`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(emptyList(), adv.invariants)
    }

    @Test
    fun `invalid invariant condition is ConditionError with path`() {
        val json = """
            {
              "title": "T",
              "invariants": [{"condition": "&& bad", "outcome": "death"}],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val errors = failure(json)
        val condErr = errors.filterIsInstance<ParseError.ConditionError>().single()
        val path = condErr.path
        assertTrue(
            path != null && path.contains("invariants[0].condition"),
            "expected path to contain 'invariants[0].condition', got $path",
        )
    }

    @Test
    fun `unknown invariant outcome is Structure error`() {
        val json = """
            {
              "title": "T",
              "invariants": [{"condition": "health <= 0", "outcome": "deth"}],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val errors = failure(json)
        val structErr = errors.filterIsInstance<ParseError.Structure>().single()
        val path = structErr.path
        assertTrue(
            path != null && path.contains("invariants[0].outcome"),
            "expected path to contain 'invariants[0].outcome', got $path",
        )
        assertTrue(
            structErr.message.contains("deth"),
            "expected message to mention 'deth', got ${structErr.message}",
        )
    }


    // ---------- Ф4.1: choice templates ----------

    @Test
    fun `choice_templates block parses`() {
        val json = """
            {
              "title": "T",
              "choice_templates": [
                {
                  "id": "give_item",
                  "parameters": [
                    {"name": "itemId", "type": "item_count"},
                    {"name": "quantity", "type": "int_min", "min": 1}
                  ],
                  "text": "Use item",
                  "next_scene": "s1"
                }
              ],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        assertEquals(1, adv.choiceTemplates.size)
        val tpl = adv.choiceTemplates.single()
        assertEquals("give_item", tpl.id)
        assertEquals(2, tpl.parameters.size)
        assertEquals("itemId", tpl.parameters[0].name)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.ItemCount,
            tpl.parameters[0].type,
        )
        assertEquals("quantity", tpl.parameters[1].name)
        assertEquals(
            com.myapp.adventure.engine.ir.types.VarType.IntMin(1),
            tpl.parameters[1].type,
        )
        assertEquals(SceneId("s1"), tpl.nextScene)
    }

    @Test
    fun `choice template visibility and availability conditions parse`() {
        val json = """
            {
              "title": "T",
              "choice_templates": [
                {
                  "id": "t1",
                  "text": "T",
                  "visibility_condition": "has_key",
                  "availability_condition": "gold > 0",
                  "next_scene": "s1"
                }
              ],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val adv = success(json)
        val tpl = adv.choiceTemplates.single()
        assertEquals(
            com.myapp.adventure.engine.ir.Condition.Has("key"),
            tpl.visibilityCondition,
        )
        assertEquals(
            com.myapp.adventure.engine.ir.Condition.Compare(
                "gold",
                com.myapp.adventure.engine.ir.CompareOp.GT,
                0,
            ),
            tpl.availabilityCondition,
        )
    }

    @Test
    fun `choice_templates absent falls back to empty list`() {
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{"id":"s1","text":"Hi"}]}]}
        """.trimIndent()
        val adv = success(json)
        assertEquals(emptyList(), adv.choiceTemplates)
    }

    @Test
    fun `duplicate choice template id is an error`() {
        val json = """
            {
              "title": "T",
              "choice_templates": [
                {"id": "dup", "text": "A", "next_scene": "s1"},
                {"id": "dup", "text": "B", "next_scene": "s1"}
              ],
              "chapters": [{"id": "c1", "scenes": [{"id": "s1", "text": "Hi"}]}]
            }
        """.trimIndent()
        val errors = failure(json)
        assertTrue(
            errors.any {
                it is ParseError.Structure && it.message.contains("Duplicate") && it.message.contains("dup")
            },
            "expected Duplicate error, got $errors",
        )
    }
}