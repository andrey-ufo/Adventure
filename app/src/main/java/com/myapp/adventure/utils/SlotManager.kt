package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlot
import com.myapp.adventure.data.SaveSlotException
import com.myapp.adventure.data.SaveSlotPaths
import com.myapp.adventure.data.SlotStateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ф5.5/Ф5.6 (S1/S2): manual save slots on SAF.
 *
 * Layout (decided in Ф5.1, see `docs/engine-saves.md`):
 * ```
 * <base adventure folder>/
 *   progress.json                 ← autosave, never touched here
 *   saves/
 *     <adventureKey>/
 *       slot-<uuid>.json          ← one exact save per file
 * ```
 *
 * S2 is structural: autosave writes only `progress.json`; slots write only
 * inside `saves/<adventureKey>/`. There is no shared file to overwrite.
 * The actual path logic lives in [SlotStore] over [SaveStorage], so the
 * same code is covered by JVM tests with an in-memory storage.
 */
object SlotManager {

    /**
     * Lists slots for [adventureFileUri], newest first.
     *
     * A corrupt/newer slot is an explicit error rather than a silently
     * hidden file: the player must know that a save exists but cannot be
     * read. The error includes the slot file name.
     */
    suspend fun listSlots(context: Context, adventureFileUri: Uri): List<SaveSlot> =
        withContext(Dispatchers.IO) {
            val (storage, key) = resolve(context, adventureFileUri) ?: return@withContext emptyList()
            SlotStore(storage).list(key)
        }

    /** Creates a new slot and returns it. Refuses at the per-adventure cap. */
    suspend fun createSlot(
        context: Context,
        adventureFileUri: Uri,
        name: String,
        state: ProgressData,
    ): SaveSlot = withContext(Dispatchers.IO) {
        val fileName = getFileNameFromUri(adventureFileUri)
            ?: throw SaveSlotException("Не удалось определить имя приключения.")
        val (storage, key) = resolve(context, adventureFileUri)
            ?: throw SaveSlotException("Не удалось открыть папку сохранений.")

        val normalized = SlotStateFactory.prepare(state, fileName)
        val slot = SlotStore(storage).create(key, name, normalized)
        Logger.log(context, "Manual slot '${slot.id}' created for $key")
        slot
    }

    /** Loads one slot, or null when the file does not exist. */
    suspend fun loadSlot(
        context: Context,
        adventureFileUri: Uri,
        slotId: String,
    ): SaveSlot? = withContext(Dispatchers.IO) {
        val (storage, key) = resolve(context, adventureFileUri) ?: return@withContext null
        SlotStore(storage).load(key, slotId)
    }

    /** Deletes one slot. Returns false when it was already gone. */
    suspend fun deleteSlot(
        context: Context,
        adventureFileUri: Uri,
        slotId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val (storage, key) = resolve(context, adventureFileUri) ?: return@withContext false
        val deleted = SlotStore(storage).delete(key, slotId)
        if (deleted) Logger.log(context, "Manual slot '$slotId' deleted for $key")
        deleted
    }

    private fun resolve(
        context: Context,
        adventureFileUri: Uri,
    ): Pair<SaveStorage, String>? {
        val fileName = getFileNameFromUri(adventureFileUri) ?: return null
        val storage = SafSaveStorage.forBaseFolder(context) ?: return null
        return storage to SaveSlotPaths.adventureKey(fileName)
    }

    private fun getFileNameFromUri(uri: Uri): String? = try {
        DocumentsContract.getDocumentId(uri)
            .substringAfterLast("/")
            .substringAfterLast(":")
    } catch (e: Exception) {
        null
    }
}
