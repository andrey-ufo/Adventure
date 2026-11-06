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
        assertEquals(Adventure.CURRENT_VERSION, adv.version) // no "version" in JSON
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

        // Гейт 100 шагов в середине главы 1 — демонстрация, что гейт
        // не привязан к границам глав.
        assertEquals(100, gateOf(0, "health_test"))
        // Гейт 5 шагов на последней сцене главы 1 — эквивалент старого
        // required_steps: 5 на главе 2.
        assertEquals(5, gateOf(0, "chapter_end_1"))
        // Гейт 4000 шагов на последней сцене главы 2 — эквивалент старого
        // required_steps: 4000 на главе 3.
        assertEquals(4000, gateOf(1, "chapter_end_2"))
        // У остальных сцен гейта нет.
        assertEquals(null, gateOf(0, "start"))
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
    fun `start scene has five choices`() {
        val adv = parseRealFile()
        val start = adv.chapters[0].scenes.first { it.id == SceneId("start") }
        assertEquals(5, start.choices.size)
    }

    @Test
    fun `has_sword condition parsed as Has AST`() {
        // Syntactic level only. Semantic rejection of `has_sword` on
        // ItemCount` is Ф2.2 (type checker). Today the parser accepts
        // any `has_X` string; the AST node is Condition.Has("sword").
        val adv = parseRealFile()
        val start = adv.chapters[0].scenes.first { it.id == SceneId("start") }
        val hasSwordChoice = start.choices.first { it.text.startsWith("Проверить условие") }
        assertEquals(Condition.Has("sword"), hasSwordChoice.condition)
    }
}