package com.myapp.adventure.engine.format

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.DeathScene
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.TagDefinition
import com.myapp.adventure.engine.ir.VarDecl
import com.myapp.adventure.engine.ir.VarType
import com.myapp.adventure.engine.ir.Gate
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Schema fingerprint: pins the **names** of every serialized field in the
 * IR, independent of values.
 *
 * **Why this test exists.** `GoldenIrTest` compares **values** against
 * `golden_ir.json` with `encodeDefaults = false`. That catches value
 * changes and renames, but it does **not** catch **added** or **removed**
 * fields that carry defaults: such a field is simply absent from the
 * serialized output, and the golden stays byte-for-byte identical. If
 * someone adds `Adventure.age: Int = 0`, or accidentally loses
 * `Adventure.version` (e.g. by removing `@Serializable` from a field
 * without changing the type), `GoldenIrTest` would stay green and the
 * schema change would ship unnoticed.
 *
 * This test closes that gap by pinning **field names and sealed variants**
 * per class. Adding, removing, or renaming any serialized field breaks the
 * test with a message naming the class. The author then either reverts
 * (unintended change) or updates the corresponding map in this test in the
 * same PR (intended change).
 *
 * **Why a hardcoded map instead of a recursive walk of
 * `serializer().descriptor`.** The hardcoded map is read by a human in the
 * PR diff: "added `age` to `Adventure` and to `expectedFlatFields`". A
 * recursive walk would also catch the change, but the diff would be
 * "test failed, fields changed" without a clear pointer to what is
 * expected. The hardcoded map **is** the documentation of the schema.
 *
 * **What this test does NOT check.** It does not pin field types,
 * nullability, default values, or field-level `@SerialName` **values**
 * (although the descriptor returns the serialized name, so a change to
 * `@SerialName("display_name")` → `@SerialName("displayName")` would show
 * up here as a rename). It also does not cover the value classes
 * (`SceneId`, `ChoiceId`, `Gate`) — those serialize transparently as
 * bare strings/ints, so they have no "fields" in the JSON to pin. If a
 * value class ever gains a custom serializer, add it here as a new entry.
 *
 * **Maintenance.** When you intentionally add/remove/rename a field or a
 * sealed variant, update the corresponding map below. The diff in your PR
 * will show both the class change and the fingerprint update side by side.
 * When you add a **new flat IR class** (a new `data class` with
 * `@Serializable`), add it to `expectedFlatFields` **and** to the
 * `flat class fields match expected fingerprint` test below.
 */
class SchemaFingerprintTest {

    private val expectedFlatFields: Map<String, Set<String>> = mapOf(
        "Adventure" to setOf(
            "version", "title", "author", "description",
            "variables", "tags", "initial_state", "death_scene",
            "start_scene", "chapters",
        ),
        "Chapter" to setOf("id", "title", "scenes"),
        "Scene" to setOf("id", "text", "effects", "choices", "gate", "is_ending"),
        "Choice" to setOf("id", "text", "next_scene", "condition", "effects"),
        "TagDefinition" to setOf("name", "display_name", "category", "visible"),
        "InitialState" to setOf("flags", "stats"),
        "DeathScene" to setOf(
            "id", "text", "allow_step_back", "allow_chapter_restart", "allow_adventure_restart",
        ),
        "VarDecl" to setOf("name", "type"),
        "Gate" to setOf("required_steps", "repeatable"),
    )

    /** Serialized variant name (from `@SerialName`) → its serialized fields. */
    private val expectedConditionVariants: Map<String, Set<String>> = mapOf(
        "has" to setOf("name"),
        "not" to setOf("inner"),
        "and" to setOf("left", "right"),
        "or" to setOf("left", "right"),
        "compare" to setOf("var_name", "op", "value"),
        "in_range" to setOf("var_name", "min", "max"),
    )

    private val expectedEffectVariants: Map<String, Set<String>> = mapOf(
        "set_flag" to setOf("name"),
        "remove_flag" to setOf("name"),
        "modify_stat" to setOf("name", "delta"),
    )

    private val expectedVarTypeVariants: Map<String, Set<String>> = mapOf(
        "bool" to emptySet(),
        "int" to emptySet(),
        "int_range" to setOf("min", "max"),
        "item_count" to emptySet(),
        "item_list" to emptySet(),
    )

    private fun fieldNames(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount)
            .map { descriptor.getElementName(it) }
            .toSet()

    /**
     * Drill into a **polymorphic** descriptor (which is how `kotlinx.serialization`
     * exposes sealed-class serializers when the sealed type is used as a
     * field or as a top-level value). The descriptor has two elements:
     * `type` (the discriminator) and `value` (the actual payload). The
     * variants live inside `value`; each variant's fields live one level
     * further.
     */
    private fun polymorphicVariants(descriptor: SerialDescriptor): Map<String, Set<String>> {
        require(descriptor.elementsCount == 2) {
            "Expected polymorphic descriptor (2 elements: type, value), got " +
                    "${descriptor.elementsCount}: " +
                    (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        }
        require(
            descriptor.getElementName(0) == "type" &&
                    descriptor.getElementName(1) == "value",
        ) {
            "Unexpected polymorphic descriptor shape: " +
                    (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        }
        val valueDescriptor = descriptor.getElementDescriptor(1)
        return (0 until valueDescriptor.elementsCount).associate { i ->
            val variantName = valueDescriptor.getElementName(i)
            val variantDescriptor = valueDescriptor.getElementDescriptor(i)
            variantName to fieldNames(variantDescriptor)
        }
    }

    private fun assertFlat(name: String, descriptor: SerialDescriptor) {
        val expected = expectedFlatFields[name]
            ?: error(
                "No entry for '$name' in expectedFlatFields. " +
                        "Add it here AND in the test that calls assertFlat('$name', ...).",
            )
        val actual = fieldNames(descriptor)
        assertEquals(
            expected,
            actual,
            "Serialized fields of $name changed. " +
                    "If the change is intentional, update expectedFlatFields in this test and commit it " +
                    "in the same PR as the IR change. If not, revert the IR change.",
        )
    }

    @Test
    fun `flat class fields match expected fingerprint`() {
        assertFlat("Adventure", Adventure.serializer().descriptor)
        assertFlat("Chapter", Chapter.serializer().descriptor)
        assertFlat("Scene", Scene.serializer().descriptor)
        assertFlat("Choice", Choice.serializer().descriptor)
        assertFlat("TagDefinition", TagDefinition.serializer().descriptor)
        assertFlat("InitialState", InitialState.serializer().descriptor)
        assertFlat("DeathScene", DeathScene.serializer().descriptor)
        assertFlat("VarDecl", VarDecl.serializer().descriptor)
        assertFlat("Gate", Gate.serializer().descriptor)
    }

    @Test
    fun `Condition sealed variants match expected fingerprint`() {
        assertEquals(
            expectedConditionVariants,
            polymorphicVariants(Condition.serializer().descriptor),
            "Condition sealed variants changed. Update expectedConditionVariants if intentional.",
        )
    }

    @Test
    fun `Effect sealed variants match expected fingerprint`() {
        assertEquals(
            expectedEffectVariants,
            polymorphicVariants(Effect.serializer().descriptor),
            "Effect sealed variants changed. Update expectedEffectVariants if intentional.",
        )
    }

    @Test
    fun `VarType sealed variants match expected fingerprint`() {
        assertEquals(
            expectedVarTypeVariants,
            polymorphicVariants(VarType.serializer().descriptor),
            "VarType sealed variants changed. Update expectedVarTypeVariants if intentional.",
        )
    }
}