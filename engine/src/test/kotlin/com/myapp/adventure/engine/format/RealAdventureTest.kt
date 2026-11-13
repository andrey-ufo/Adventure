package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory
import com.myapp.adventure.engine.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import com.myapp.adventure.engine.ir.CompareOp

/**
 * Integration test: parses the **real** `test_adventure.json` (copied
 * from `:app` into `:engine` test resources by the Gradle copy task in
 * `engine/build.gradle.kts`, see Ф1.3.3.1) and asserts the resulting IR
 * matches the file's documented contents.
 *
 * **Why this test exists:** `test_adventure.json` is shipped to every new
 * user (SetupFragment copies it into their `Adventures/` folder on first
 * launch). If the engine cannot parse it, the very first adventure the
 * user opens is broken. This test fails in CI **before** a broken file
 * reaches a user.
 *
 * **What this test does NOT check** (deliberately deferred):
 * - Type validity of `has_sword` on `sword: ItemCount` — type checker
 *   Ф2.2. Today `sword` is `ItemCount`, but `test_adventure.json` still
 *   uses `has_sword`. This will be rewritten in Ф2.8 to `sword > 0`.
 * - `next_scene` reachability, duplicate scene ids — `Analyzer` (Ф1.6).
 *
 * If this test fails after you changed `test_adventure.json`, it is
 * working as designed: update the test (both are in the same repo) or,
 * if the change is a bug in the engine, fix the engine.
 */
class RealAdventureTest {

    private fun loadAdventureJson(): String {
        val stream = javaClass.getResourceAsStream("/test_adventure.json")
        assertNotNull(stream, "test_adventure.json not found in test resources — check the copy task in engine/build.gradle.kts")
        return stream.bufferedReader().use { it.readText() }
    }

    private fun parseRealFile(): Adventure {
        val json = loadAdventureJson()
        val result = JsonToIr.parse(json)
        val success = assertIs<ParseResult.Success<Adventure>>(
            result,
            "real test_adventure.json failed to parse: $result",
        )
        return success.value
    }

    @Test
    fun `parses without errors`() {
        // If the real file fails to parse, CI fails here before a new user
        // sees a broken sample on first launch.
        parseRealFile()
    }

    @Test
    fun `start scene is start`() {
        val adv = parseRealFile()
        assertEquals(SceneId("start"), adv.startScene)
    }

    @Test
    fun `metadata`() {
        val adv = parseRealFile()
        assertEquals("Тестовое приключение", adv.title)
        assertEquals("Claude (AI-ассистент)", adv.author)
        // test_adventure.json explicitly carries "version": CURRENT_VERSION.
        // It is the reference sample — a reader opening it should see the
        // current schema, not a legacy one. When CURRENT_VERSION bumps, the
        // sample bumps in the same PR; this test catches a forgotten bump.
        assertEquals(Adventure.CURRENT_VERSION, adv.version)
        assertTrue(adv.description.isNotEmpty())
    }

    @Test
    fun `three chapters with gates on specific scenes`() {
        val adv = parseRealFile()
        assertEquals(3, adv.chapters.size)
        assertEquals("chapter_1", adv.chapters[0].id)
        assertEquals("chapter_2", adv.chapters[1].id)
        assertEquals("chapter_3", adv.chapters[2].id)
        // Главы больше не несут гейт — это чисто нарративная группировка.
        // Гейты живут на конкретных сценах (Ф1.5.4).
    }

    @Test
    fun `gates on specific scenes`() {
        val adv = parseRealFile()
        fun gateOf(chapterIndex: Int, sceneId: String): Int? =
            adv.chapters[chapterIndex].scenes
                .first { it.id == SceneId(sceneId) }
                .gate
                ?.requiredSteps

        // Все три гейта в эталонном приключении — 1 шаг: достаточно,
        // чтобы проверить механику вручную (см. проектный принцип
        // «тестовое приключение должно проходиться быстро»), и не
        // заставляет тестировщика ходить. Реальные приключения,
        // разумеется, ставят тысячи шагов.
        //
        // Ф3.4-fix2: гейт вынесен из `health_test` в отдельную сцену
        // `gate_test`, чтобы тестировать здоровье/предметы/журнал без
        // шагов. `health_test` теперь без гейта. `gate_test` — per-visit
        // режим. `chapter_end_*` — one-shot, нарративные, тоже 1 шаг.
        assertEquals(1, gateOf(0, "gate_test"))     // per-visit gate (middle of chapter 1)
        assertEquals(1, gateOf(0, "chapter_end_1")) // one-shot gate (end of chapter 1)
        assertEquals(1, gateOf(1, "chapter_end_2")) // one-shot gate (end of chapter 2)
        // У остальных сцен гейта нет.
        assertEquals(null, gateOf(0, "start"))
        assertEquals(null, gateOf(0, "health_test"))
        assertEquals(null, gateOf(0, "items_test"))
        assertEquals(null, gateOf(1, "conditions_test"))
        assertEquals(null, gateOf(2, "final"))
    }

    @Test
    fun `cross-chapter next_scene is allowed`() {
        val adv = parseRealFile()
        // Демонстрация: сцена из главы 2 может вести в сцену главы 3
        // напрямую. Это разрешено: главы — нарративная группировка, а не
        // барьеры. Гейт 4000 шагов на chapter_end_2 обходится — автор
        // делает это намеренно (демо для тестового приключения).
        val conditions = adv.chapters[1].scenes.first { it.id == SceneId("conditions_test") }
        val jumpChoice = conditions.choices.first {
            it.nextScene == SceneId("final")
        }
        assertEquals("В финал (демо межглавного перехода)", jumpChoice.text)
    }

    @Test
    fun `death scene present and fully enabled`() {
        val adv = parseRealFile()
        val death = assertNotNull(adv.deathScene)
        assertEquals(SceneId("death_scene"), death.id)
        assertTrue(death.allowStepBack)
        assertTrue(death.allowChapterRestart)
        assertTrue(death.allowAdventureRestart)
    }

    @Test
    fun `tags registry`() {
        val adv = parseRealFile()
        assertEquals(11, adv.tags.size)
        val health = adv.tags.first { it.name == "health" }
        assertEquals(TagCategory.HEALTH, health.category)
        assertTrue(health.visible)
        val sword = adv.tags.first { it.name == "sword" }
        assertEquals(TagCategory.ITEM, sword.category)
        assertEquals("Ржавый меч", sword.displayName)
    }

    @Test
    fun `initial state`() {
        val adv = parseRealFile()
        assertEquals(listOf("initial_flag"), adv.initialState.flags)
        assertEquals(100, adv.initialState.stats["health"])
        assertEquals(3, adv.initialState.stats["potion"])
    }

    @Test
    fun `start scene has seven choices`() {
        val adv = parseRealFile()
        val start = adv.chapters[0].scenes.first { it.id == SceneId("start") }
        assertEquals(7, start.choices.size)
    }

    @Test
    fun `sword_gt_zero condition parsed as Compare AST`() {
        // Ф1.7.1a-fix3: the sample used `has_sword` (legacy semantics,
        // where `has_X` was true if X was a flag OR a positive stat).
        // Since Ф1.5, `has_X` means "boolean flag X is set" and does
        // NOT look at stats. `sword` is an ItemCount, so `has_sword`
        // evaluated to false — the button was always disabled. The
        // sample now uses `sword > 0`, which matches the new
        // semantics. Type checker (Ф2.2) will reject the legacy form
        // with a hint to use `Compare` instead.
        val adv = parseRealFile()
        val start = adv.chapters[0].scenes.first { it.id == SceneId("start") }
        val hasSwordChoice = start.choices.first { it.text.startsWith("Проверить условие") }
        assertEquals(
            Condition.Compare("sword", CompareOp.GT, 0),
            hasSwordChoice.condition,
        )
    }

    @Test
    fun `final scene is marked as ending`() {
        // Ф1.7.0: the reference sample's `final` scene must be marked
        // "is_ending": true. The old behavior ("no choices = implicit
        // end") was dropped; the analyzer now flags an unmarked terminal
        // as DeadEnd ERROR. If this assertion fails, either the file was
        // edited (check docs/test-adventure-policy.md Rule 7) or the
        // parser stopped reading is_ending (bug in JsonToIr).
        val adv = parseRealFile()
        val final = adv.chapters[2].scenes.first { it.id == SceneId("final") }
        assertTrue(final.isEnding, "final scene must have is_ending = true")
        assertTrue(final.choices.isEmpty(), "final scene must have no choices")
    }

    @Test
    fun `gate_test is repeatable, chapter ends are not`() {
        // Ф1.7.0.4: the only "path" example (you walk there, walk back
        // if you return) is gate_test — per-visit. The chapter-end
        // gates use the default "once passed, stays passed in this
        // playthrough" (repeatable = false). This test pins both so that
        // the reference sample keeps demonstrating both modes.
        //
        // Ф3.4-fix2: gate moved from `health_test` to `gate_test` so
        // that testing health/items/journal does not require walking.
        val adv = parseRealFile()
        fun gateOf(chapterIndex: Int, sceneId: String) =
            adv.chapters[chapterIndex].scenes
                .first { it.id == SceneId(sceneId) }
                .gate

        assertEquals(true, gateOf(0, "gate_test")?.repeatable)
        assertEquals(false, gateOf(0, "chapter_end_1")?.repeatable)
        assertEquals(false, gateOf(1, "chapter_end_2")?.repeatable)
    }

    @Test
    fun `invariants has health rule`() {
        // Ф3.5: reference sample declares health <= 0 => death,
        // matching the pre-Ф3.5 fallback but explicit. This test pins
        // the format and one semantic check.
        val adv = parseRealFile()
        assertEquals(1, adv.invariants.size)
        val inv = adv.invariants.single()
        assertEquals(
            com.myapp.adventure.engine.ir.Outcome.Death,
            inv.outcome,
        )
        val condition = inv.condition
        assertIs<com.myapp.adventure.engine.ir.Condition.Compare>(condition)
        assertEquals(
            com.myapp.adventure.engine.ir.CompareOp.LE,
            condition.op,
        )
    }
}