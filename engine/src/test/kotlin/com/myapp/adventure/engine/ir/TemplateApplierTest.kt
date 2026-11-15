package com.myapp.adventure.engine.ir

import com.myapp.adventure.engine.ir.types.VarDecl
import com.myapp.adventure.engine.ir.types.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateApplierTest {

    // ---------- helpers ----------

    private fun template(
        id: String = "use_item",
        parameters: List<VarDecl> = listOf(VarDecl("itemId", VarType.ItemCount)),
        text: String = "Use {itemId}",
        visibilityCondition: Condition? = null,
        availabilityCondition: Condition? = null,
        effects: List<Effect> = emptyList(),
        nextScene: String = "back",
    ): ChoiceTemplate = ChoiceTemplate(
        id = id,
        parameters = parameters,
        text = text,
        visibilityCondition = visibilityCondition,
        availabilityCondition = availabilityCondition,
        effects = effects,
        nextScene = SceneId(nextScene),
    )

    // ---------- happy path ----------

    @Test
    fun `text placeholder substituted`() {
        val c = applyTemplate(
            template = template(),
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
        )
        assertEquals("Use potion", c.text)
    }

    @Test
    fun `different args produce different text`() {
        val t = template()
        val potion = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        val sword = applyTemplate(t, mapOf("itemId" to "sword"), ChoiceId("c2"))
        assertEquals("Use potion", potion.text)
        assertEquals("Use sword", sword.text)
        assertTrue(potion.text != sword.text)
    }

    @Test
    fun `availability condition substituted - different args produce different conditions`() {
        val t = template(
            availabilityCondition = Condition.Compare("itemId", CompareOp.GT, 0),
        )
        val potion = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        val sword = applyTemplate(t, mapOf("itemId" to "sword"), ChoiceId("c2"))
        assertEquals(
            Condition.Compare("potion", CompareOp.GT, 0),
            potion.condition,
        )
        assertEquals(
            Condition.Compare("sword", CompareOp.GT, 0),
            sword.condition,
        )
    }

    @Test
    fun `visibility condition substituted`() {
        val t = template(
            visibilityCondition = Condition.Has("itemId"),
        )
        val c = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        assertEquals(Condition.Has("potion"), c.visibilityCondition)
    }

    @Test
    fun `effects substituted - different args produce different effects`() {
        // B1 semantics: only exact name-slot matches are substituted.
        // Both sides of a two-effect list would need to reference the
        // same parameter exactly. One effect is enough to demonstrate
        // "different args → different effects" (the DoD requirement).
        val t = template(
            effects = listOf(Effect.ModifyStat("itemId", -1)),
        )
        val potion = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        val sword = applyTemplate(t, mapOf("itemId" to "sword"), ChoiceId("c2"))
        assertEquals(
            listOf(Effect.ModifyStat("potion", -1)),
            potion.effects,
        )
        assertEquals(
            listOf(Effect.ModifyStat("sword", -1)),
            sword.effects,
        )
    }

    @Test
    fun `nested condition substituted - And`() {
        // B1 semantics: exact match. `other_flag` is not a parameter and
        // stays as-is; `itemId` is a parameter and gets substituted inside
        // the And tree. This demonstrates that substitution recurses into
        // nested conditions.
        val t = template(
            availabilityCondition = Condition.And(
                Condition.Has("other_flag"),
                Condition.Compare("itemId", CompareOp.GT, 0),
            ),
        )
        val c = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        assertEquals(
            Condition.And(
                Condition.Has("other_flag"),
                Condition.Compare("potion", CompareOp.GT, 0),
            ),
            c.condition,
        )
    }

    @Test
    fun `InRange substituted`() {
        val t = template(
            parameters = listOf(VarDecl("stat", VarType.IntRange(0, 100))),
            text = "{stat}",
            availabilityCondition = Condition.InRange("stat", 0, 100),
        )
        val c = applyTemplate(t, mapOf("stat" to "health"), ChoiceId("c1"))
        assertEquals(Condition.InRange("health", 0, 100), c.condition)
    }

    @Test
    fun `Effect Set name substituted, value not`() {
        val t = template(
            parameters = listOf(VarDecl("stat", VarType.IntMin(0))),
            text = "{stat}",
            effects = listOf(Effect.Set("stat", 42)),
        )
        val c = applyTemplate(t, mapOf("stat" to "health"), ChoiceId("c1"))
        assertEquals(listOf(Effect.Set("health", 42)), c.effects)
    }

    @Test
    fun `non-parameter names left as-is`() {
        // Template references a global variable `health` (not a parameter).
        val t = template(
            availabilityCondition = Condition.Compare("health", CompareOp.GT, 0),
        )
        val c = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        assertEquals(
            Condition.Compare("health", CompareOp.GT, 0),
            c.condition,
        )
    }

    @Test
    fun `prefix composition is not substituted - exact match only (B1)`() {
        // B1 decision (Ф4.2.1): exact match only in AST name slots.
        // `has_itemId` is a **composed** name, not `has_` + parameter —
        // it does not equal any parameter name, so it stays as-is.
        // This is by design: predictable semantics, no ambiguity about
        // which substring is a parameter. Authors who need `has_potion`
        // derived from `itemId` pass a **dedicated Bool parameter**:
        //   parameters: [itemId: ItemCount, hasItem: Bool]
        //   args: {itemId: "potion", hasItem: "has_potion"}
        //   template uses: Has("hasItem") → Has("has_potion")
        // The alternative (prefix substitution) was rejected: it would
        // require a disambiguation rule (`has_` is reserved? what about
        // a literal flag named `has_itemId`?) and would complicate
        // future JSON parsing. See `docs/engine-architecture.md`
        // §"Choice templates" for the record.
        //
        // This test pins the current behaviour so a future "improvement"
        // to prefix substitution is a deliberate decision, not an
        // accidental one.
        val t = template(
            availabilityCondition = Condition.Has("has_itemId"),
        )
        val c = applyTemplate(t, mapOf("itemId" to "potion"), ChoiceId("c1"))
        assertEquals(
            Condition.Has("has_itemId"),
            c.condition,
            "Prefix composition must NOT be substituted (B1). " +
                    "If you want to change this, update the B1 decision record " +
                    "in docs/engine-architecture.md and this test in the same PR.",
        )
    }

    @Test
    fun `templateRef set on expanded choice`() {
        val c = applyTemplate(
            template = template(),
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
        )
        val ref = assertNotNull(c.templateRef)
        assertEquals("use_item", ref.templateId)
        assertEquals(mapOf("itemId" to "potion"), ref.args)
    }

    @Test
    fun `nextScene not substituted`() {
        val t = template(
            parameters = listOf(VarDecl("sceneId", VarType.Bool)),
            nextScene = "back",
        )
        val c = applyTemplate(t, mapOf("sceneId" to "somewhere"), ChoiceId("c1"))
        assertEquals(SceneId("back"), c.nextScene)
    }

    // ---------- validation ----------

    @Test
    fun `missing parameter argument throws`() {
        assertFailsWith<IllegalArgumentException> {
            applyTemplate(
                template = template(),
                args = emptyMap(),
                choiceId = ChoiceId("c1"),
            )
        }
    }

    @Test
    fun `extra argument throws`() {
        assertFailsWith<IllegalArgumentException> {
            applyTemplate(
                template = template(),
                args = mapOf("itemId" to "potion", "typo" to "x"),
                choiceId = ChoiceId("c1"),
            )
        }
    }

    @Test
    fun `no parameters - empty args works`() {
        val t = template(
            parameters = emptyList(),
            text = "Leave",
            availabilityCondition = null,
        )
        val c = applyTemplate(t, emptyMap(), ChoiceId("c1"))
        assertEquals("Leave", c.text)
    }

    // ---------- Adventure extension ----------

    @Test
    fun `Adventure applyTemplate finds template by id`() {
        val t = template()
        val adventure = Adventure(
            title = "T",
            startScene = SceneId("s1"),
            choiceTemplates = listOf(t),
            chapters = listOf(
                Chapter(
                    id = "ch",
                    title = "ch",
                    scenes = listOf(Scene(SceneId("s1"), "text", isEnding = true)),
                ),
            ),
        )
        val c = adventure.applyTemplate(
            templateId = "use_item",
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
        )
        assertNotNull(c)
        assertEquals("Use potion", c.text)
    }

    @Test
    fun `Adventure applyTemplate returns null for missing template`() {
        val adventure = Adventure(
            title = "T",
            startScene = SceneId("s1"),
            chapters = listOf(
                Chapter(
                    id = "ch",
                    title = "ch",
                    scenes = listOf(Scene(SceneId("s1"), "text", isEnding = true)),
                ),
            ),
        )
        val c = adventure.applyTemplate("no_such_template", emptyMap(), ChoiceId("c1"))
        assertNull(c)
    }


    // ---------- Ф4.3.2a: @self ----------

    @Test
    fun `@self resolved to currentSceneId when provided`() {
        val t = template(
            nextScene = "@self",
        )
        val c = applyTemplate(
            template = t,
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
            currentSceneId = SceneId("forest_path"),
        )
        assertEquals(SceneId("forest_path"), c.nextScene)
    }

    @Test
    fun `@self without currentSceneId throws`() {
        val t = template(
            nextScene = "@self",
        )
        assertFailsWith<IllegalArgumentException> {
            applyTemplate(
                template = t,
                args = mapOf("itemId" to "potion"),
                choiceId = ChoiceId("c1"),
            )
        }
    }

    @Test
    fun `Adventure applyTemplate forwards currentSceneId`() {
        val t = template(nextScene = "@self")
        val adventure = Adventure(
            title = "T",
            startScene = SceneId("s1"),
            choiceTemplates = listOf(t),
            chapters = listOf(
                Chapter(
                    id = "ch",
                    title = "ch",
                    scenes = listOf(Scene(SceneId("s1"), "text", isEnding = true)),
                ),
            ),
        )
        val c = adventure.applyTemplate(
            templateId = "use_item",
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
            currentSceneId = SceneId("s1"),
        )
        assertNotNull(c)
        assertEquals(SceneId("s1"), c.nextScene)
    }

    @Test
    fun `non-@self nextScene ignores currentSceneId`() {
        // Sanity: currentSceneId is not a general "overwrite nextScene"
        // knob — only `@self` uses it.
        val t = template(nextScene = "back")
        val c = applyTemplate(
            template = t,
            args = mapOf("itemId" to "potion"),
            choiceId = ChoiceId("c1"),
            currentSceneId = SceneId("forest_path"),
        )
        assertEquals(SceneId("back"), c.nextScene)
    }
}