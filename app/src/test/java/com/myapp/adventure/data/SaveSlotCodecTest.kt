package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.utils.ProgressMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/** Ф5.5 (S1): slot codec, version checks and stable adventure keys. */
class SaveSlotCodecTest {

    private fun baseState(): ProgressData = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = "my_adventure.json",
        baseSnapshot = ProgressSnapshot(
            currentChapterIndex = 0,
            currentSceneId = "start",
            flags = listOf("started"),
            stats = mapOf("health" to 10),
            itemOrder = listOf("potion"),
        ),
        delta = listOf(StateDelta.AddStat("health", -3).toDto()),
        currentChapterIndex = 0,
        currentSceneId = "start",
        flags = listOf("started"),
        stats = mapOf("health" to 7),
        itemOrder = listOf("potion"),
        passedGates = listOf("gate_1"),
    )

    @Test
    fun roundTripPreservesMetadataAndExactState() {
        val slot = SaveSlot(
            id = "4f6c8e0a-1111",
            name = "Перед подземельем",
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
            state = baseState(),
        )

        val json = SaveSlotCodec.encode(slot, "my_adventure-abcd1234")
        val decoded = SaveSlotCodec.decode(json)

        assertEquals(slot, decoded)
    }

    @Test
    fun unknownFormatVersionIsExplicitError() {
        val json = """
            {
              "format_version": 2,
              "slot": {"id": "s1", "name": "future", "created_at": 1, "updated_at": 2},
              "adventure_key": "q-abcd",
              "state": {"version": 1, "adventureFileName": "q.json"}
            }
        """.trimIndent()

        assertThrows(SaveSlotException::class.java) {
            SaveSlotCodec.decode(json)
        }
    }

    @Test
    fun missingStateIsExplicitError() {
        val json = """
            {
              "format_version": 1,
              "slot": {"id": "s1", "name": "empty", "created_at": 1, "updated_at": 2},
              "adventure_key": "q-abcd"
            }
        """.trimIndent()

        assertThrows(SaveSlotException::class.java) {
            SaveSlotCodec.decode(json)
        }
    }

    @Test
    fun legacyEmbeddedStateIsMigratedAndNormalized() {
        val json = """
            {
              "format_version": 1,
              "slot": {"id": "s1", "name": "legacy", "created_at": 1, "updated_at": 2},
              "adventure_key": "q-abcd",
              "state": {"adventureFileName": "q.json", "currentSceneId": "s"}
            }
        """.trimIndent()

        val decoded = SaveSlotCodec.decode(json)

        assertEquals(ProgressMigration.CURRENT_VERSION, decoded.state.version)
        assertTrue(decoded.state.flags.isEmpty())
        assertTrue(decoded.state.stats.isEmpty())
        assertEquals("s", decoded.state.currentSceneId)
    }

    @Test
    fun adventureKeyIsStableAndSanitized() {
        val key = SaveSlotPaths.adventureKey("my adventure.json")

        assertTrue(key.startsWith("my_adventure-"))
        assertEquals(8, key.substringAfterLast('-').length)
        assertEquals(key, SaveSlotPaths.adventureKey("my adventure.json"))
        assertNotEquals(key, SaveSlotPaths.adventureKey("my_adventure.json"))
    }

    @Test
    fun malformedJsonIsExplicitError() {
        assertThrows(SaveSlotException::class.java) {
            SaveSlotCodec.decode("{ not a slot")
        }
    }

    @Test
    fun missingIdIsExplicitError() {
        val json = """
            {
              "format_version": 1,
              "slot": {"name": "no id", "created_at": 1, "updated_at": 2},
              "state": {"version": 1, "adventureFileName": "q.json"}
            }
        """.trimIndent()

        assertThrows(SaveSlotException::class.java) {
            SaveSlotCodec.decode(json)
        }
    }

    @Test
    fun futureEmbeddedStateVersionIsExplicitError() {
        val json = """
            {
              "format_version": 1,
              "slot": {"id": "s1", "name": "future", "created_at": 1, "updated_at": 2},
              "state": {"version": 999, "adventureFileName": "q.json"}
            }
        """.trimIndent()

        assertThrows(SaveSlotException::class.java) {
            SaveSlotCodec.decode(json)
        }
    }

    @Test
    fun blankNameFallsBackToId() {
        val json = """
            {
              "format_version": 1,
              "slot": {"id": "s1", "name": "   ", "created_at": 1, "updated_at": 2},
              "state": {"version": 1, "adventureFileName": "q.json"}
            }
        """.trimIndent()

        assertEquals("s1", SaveSlotCodec.decode(json).name)
    }

    @Test
    fun adventureKeyHandlesNoExtensionAndLongUnicodeNames() {
        val noExtension = SaveSlotPaths.adventureKey("noext")
        assertTrue(noExtension.startsWith("noext-"))

        val longUnicode = SaveSlotPaths.adventureKey(
            "Приключение с очень длинным названием, которое точно больше сорока символов.json",
        )
        assertTrue(longUnicode.substringBeforeLast('-').length <= 40)
        assertTrue(longUnicode.matches(Regex("[a-z0-9_-]+")))
        assertEquals(8, longUnicode.substringAfterLast('-').length)
    }

    @Test
    fun slotFileNameRoundTripsToId() {
        val id = SaveSlotCodec.newId()
        val fileName = SaveSlotPaths.slotFileName(id)

        assertTrue(SaveSlotPaths.isSlotFileName(fileName))
        assertEquals(id, SaveSlotPaths.idFromFileName(fileName))
    }

    @Test
    fun capIsTwentySlotsPerAdventure() {
        assertEquals(20, SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE)
    }
}
