package com.myapp.adventure.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.myapp.adventure.utils.ProgressMigration
import com.myapp.adventure.utils.ProgressMigrationException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Ф5.5: one manual save slot, with its exact [ProgressData] state. */
data class SaveSlot(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: ProgressData,
)

/** User-facing slot errors (shown by the reader slots screen). */
class SaveSlotException(message: String) : Exception(message)

/**
 * Pure path/key helpers for slots. Kept free of Android dependencies so
 * they can be unit-tested on the JVM.
 */
object SaveSlotPaths {

    const val SAVES_DIRECTORY = "saves"
    const val MAX_SLOTS_PER_ADVENTURE = 20

    /**
     * `adventureKey` = sanitized adventure file name + short stable hash.
     *
     * The hash is over the full file name, so `my adventure.json` and
     * `my_adventure.json` never share a folder. The readable part is
     * cosmetic; ids are never parsed back into something meaningful.
     */
    fun adventureKey(adventureFileName: String): String {
        val bareName = adventureFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .let { it.substringBeforeLast('.', it) }
        val sanitized = bareName
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "adventure" }
            .take(40)
        return "$sanitized-${sha256(adventureFileName).take(8)}"
    }

    fun slotFileName(id: String): String = "slot-$id.json"

    fun isSlotFileName(name: String): Boolean =
        name.startsWith("slot-") && name.endsWith(".json")

    fun idFromFileName(name: String): String =
        name.removePrefix("slot-").removeSuffix(".json")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

/**
 * On-disk codec for a manual slot.
 *
 * Shape (matches `docs/engine-saves.md`):
 * ```
 * { "format_version": 1,
 *   "slot": { "id": ..., "name": ..., "created_at": ..., "updated_at": ... },
 *   "adventure_key": ...,
 *   "state": <ProgressData> }
 * ```
 * The embedded state uses the same versioning/normalization as
 * `progress.json`, so a slot and the autosave cannot drift schema-wise.
 */
object SaveSlotCodec {

    const val FORMAT_VERSION: Int = 1

    private val gson = Gson()

    fun newId(): String = UUID.randomUUID().toString()

    fun encode(slot: SaveSlot, adventureKey: String): String {
        val wire = SlotFileWire(
            formatVersion = FORMAT_VERSION,
            slot = SlotMetaWire(
                id = slot.id,
                name = slot.name,
                createdAt = slot.createdAt,
                updatedAt = slot.updatedAt,
            ),
            adventureKey = adventureKey,
            state = slot.state,
        )
        return gson.toJson(wire)
    }

    fun decode(json: String): SaveSlot {
        val wire = try {
            gson.fromJson(json, SlotFileWire::class.java)
        } catch (e: Exception) {
            throw SaveSlotException("Файл сохранения повреждён: ${e.message}")
        } ?: throw SaveSlotException("Файл сохранения пуст.")

        if (wire.formatVersion != FORMAT_VERSION) {
            throw SaveSlotException(
                "Сохранение использует формат ${wire.formatVersion}, " +
                        "а приложение поддерживает $FORMAT_VERSION. Обновите приложение.",
            )
        }

        val meta = wire.slot ?: throw SaveSlotException("В сохранении нет данных слота.")
        val savedState = wire.state
            ?: throw SaveSlotException("В сохранении нет состояния игры.")
        val id = meta.id?.takeIf { it.isNotBlank() }
            ?: throw SaveSlotException("В сохранении нет идентификатора.")
        val name = meta.name?.takeIf { it.isNotBlank() } ?: id
        val createdAt = meta.createdAt ?: 0L
        val updatedAt = meta.updatedAt ?: createdAt

        val state = try {
            ProgressMigration.migrate(savedState)
        } catch (e: ProgressMigrationException) {
            throw SaveSlotException(e.message ?: "Не удалось прочитать состояние сохранения.")
        }

        return SaveSlot(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            state = state,
        )
    }

    private data class SlotFileWire(
        @SerializedName("format_version") val formatVersion: Int = 0,
        val slot: SlotMetaWire? = null,
        @SerializedName("adventure_key") val adventureKey: String? = null,
        val state: ProgressData? = null,
    )

    private data class SlotMetaWire(
        val id: String? = null,
        val name: String? = null,
        @SerializedName("created_at") val createdAt: Long? = null,
        @SerializedName("updated_at") val updatedAt: Long? = null,
    )
}
