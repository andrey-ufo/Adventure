package com.myapp.adventure.utils

import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlot
import com.myapp.adventure.data.SaveSlotCodec
import com.myapp.adventure.data.SaveSlotException
import com.myapp.adventure.data.SaveSlotPaths

/**
 * Ф5.6: pure slot persistence over [SaveStorage].
 *
 * All slot paths are under `saves/<adventureKey>/`; this class never
 * reads or writes `progress.json`. That separation is what makes S2
 * testable without Android: an in-memory [SaveStorage] gives the exact
 * same path semantics.
 */
internal class SlotStore(private val storage: SaveStorage) {

    fun list(adventureKey: String): List<SaveSlot> {
        val directory = directory(adventureKey)
        return storage.listDocuments(directory)
            .filter(SaveSlotPaths::isSlotFileName)
            .map { fileName ->
                val json = storage.readDocument(directory + fileName)
                    ?: throw SaveSlotException("$fileName: файл недоступен.")
                decode(fileName, json)
            }
            .sortedByDescending { it.updatedAt }
    }

    fun create(
        adventureKey: String,
        name: String,
        state: ProgressData,
        now: Long = System.currentTimeMillis(),
    ): SaveSlot {
        val existing = storage.listDocuments(directory(adventureKey))
            .count(SaveSlotPaths::isSlotFileName)
        if (existing >= SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE) {
            throw SaveSlotException(
                "Достигнут лимит сохранений (${SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE}). " +
                        "Удалите одно из старых.",
            )
        }

        val slot = SaveSlot(
            id = SaveSlotCodec.newId(),
            name = name.trim().ifBlank { "Сохранение" },
            createdAt = now,
            updatedAt = now,
            state = state,
        )
        val fileName = SaveSlotPaths.slotFileName(slot.id)
        val written = storage.writeDocument(
            directory(adventureKey) + fileName,
            SaveSlotCodec.encode(slot, adventureKey),
        )
        if (!written) throw SaveSlotException("Не удалось записать сохранение.")
        return slot
    }

    fun load(adventureKey: String, slotId: String): SaveSlot? {
        val fileName = SaveSlotPaths.slotFileName(slotId)
        val json = storage.readDocument(directory(adventureKey) + fileName) ?: return null
        return decode(fileName, json)
    }

    fun delete(adventureKey: String, slotId: String): Boolean =
        storage.deleteDocument(
            directory(adventureKey) + SaveSlotPaths.slotFileName(slotId),
        )

    private fun decode(fileName: String, json: String): SaveSlot =
        try {
            SaveSlotCodec.decode(json)
        } catch (e: SaveSlotException) {
            throw SaveSlotException("$fileName: ${e.message}")
        }

    private fun directory(adventureKey: String): List<String> =
        listOf(SaveSlotPaths.SAVES_DIRECTORY, adventureKey)
}
