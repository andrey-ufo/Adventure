package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectsApplierTest {

    @Test
    fun `empty effects return same state`() {
        val s = PlayerState(flags = setOf("a"), stats = mapOf("health" to 10))
        assertEquals(s, EffectsApplier.apply(s, emptyList()))
    }

    @Test
    fun `set flag adds`() {
        val s = EffectsApplier.apply(
            PlayerState(),
            listOf(Effect.SetFlag("a"), Effect.SetFlag("b")),
        )
        assertEquals(setOf("a", "b"), s.flags)
    }

    @Test
    fun `set flag is idempotent`() {
        val s = EffectsApplier.apply(
            PlayerState(flags = setOf("a")),
            listOf(Effect.SetFlag("a")),
        )
        assertEquals(setOf("a"), s.flags)
    }

    @Test
    fun `remove flag removes`() {
        val s = EffectsApplier.apply(
            PlayerState(flags = setOf("a", "b")),
            listOf(Effect.RemoveFlag("a")),
        )
        assertEquals(setOf("b"), s.flags)
    }

    @Test
    fun `modify stat adds delta`() {
        val s = EffectsApplier.apply(
            PlayerState(stats = mapOf("health" to 100)),
            listOf(Effect.ModifyStat("health", -30)),
        )
        assertEquals(70, s.stats["health"])
    }

    @Test
    fun `modify stat creates missing with delta`() {
        val s = EffectsApplier.apply(
            PlayerState(),
            listOf(Effect.ModifyStat("mana", 5)),
        )
        assertEquals(5, s.stats["mana"])
    }

    @Test
    fun `modify stat clamps to zero`() {
        // Ф1.5 policy: all ModifyStat clamp to >= 0. In Ф2.8 this becomes
        // type-dependent (HEALTH/ITEM clamp, Integer/IntRange per-type).
        val s = EffectsApplier.apply(
            PlayerState(stats = mapOf("health" to 3)),
            listOf(Effect.ModifyStat("health", -10)),
        )
        assertEquals(0, s.stats["health"])
    }

    @Test
    fun `order matters - remove then set leaves set`() {
        val s = EffectsApplier.apply(
            PlayerState(flags = setOf("a")),
            listOf(Effect.RemoveFlag("a"), Effect.SetFlag("a")),
        )
        assertEquals(setOf("a"), s.flags)
    }

    @Test
    fun `order matters - set then remove leaves unset`() {
        val s = EffectsApplier.apply(
            PlayerState(),
            listOf(Effect.SetFlag("a"), Effect.RemoveFlag("a")),
        )
        assertEquals(emptySet(), s.flags)
    }

    @Test
    fun `state is not mutated`() {
        val original = PlayerState(stats = mapOf("health" to 10))
        EffectsApplier.apply(original, listOf(Effect.ModifyStat("health", -5)))
        assertEquals(10, original.stats["health"])
    }

    // ---------- Ф3.1: Effect.Set ----------

    @Test
    fun `set stat sets absolute value`() {
        // Pre-existing value 50 → 100 (not 100 + 50).
        val s = EffectsApplier.apply(
            PlayerState(stats = mapOf("health" to 50)),
            listOf(Effect.Set("health", 100)),
        )
        assertEquals(100, s.stats["health"])
    }

    @Test
    fun `set stat creates missing stat with value`() {
        val s = EffectsApplier.apply(
            PlayerState(),
            listOf(Effect.Set("mana", 5)),
        )
        assertEquals(5, s.stats["mana"])
    }

    @Test
    fun `set stat clamps to zero`() {
        // Same clamp policy as ModifyStat (Ф5 will make it type-aware).
        val s = EffectsApplier.apply(
            PlayerState(stats = mapOf("health" to 50)),
            listOf(Effect.Set("health", -10)),
        )
        assertEquals(0, s.stats["health"])
    }

    @Test
    fun `state is not mutated by set`() {
        val original = PlayerState(stats = mapOf("health" to 50))
        EffectsApplier.apply(original, listOf(Effect.Set("health", 100)))
        assertEquals(50, original.stats["health"], "original state must not be mutated")
    }
}