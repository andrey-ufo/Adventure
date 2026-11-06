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
        assertEquals(3, adv.version) // CURRENT_VERSION after Ф1.6.0
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
    fun `typo in flag name parses as Has - type check is F2`() {
        // `has_swrod` (typo for `sword`) parses successfully: the parser
        // cannot know whether `swrod` is a declared variable. Catching
        // typos is the type checker's job (Ф2.2), which will report
        // "unknown variable 'swrod'" against [Adventure.variables].
        //
        // This test documents the parser/analyzer boundary. If it starts
        // failing, someone moved a semantic check into the parser and
        // broke single-responsibility of each layer.
        val json = """
            {"title":"T","chapters":[{"id":"c1","scenes":[{
              "id":"s1","text":"Hi","choices":[{"text":"Go","next_scene":"s1","condition":"has_swrod"}]
            }]}]}
        """.trimIndent()
        val adv = success(json)
        val choice = adv.chapters[0].scenes[0].choices[0]
        assertEquals(Condition.Has("swrod"), choice.condition)
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
}