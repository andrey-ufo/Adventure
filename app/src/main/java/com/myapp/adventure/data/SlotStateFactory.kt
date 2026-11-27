package com.myapp.adventure.data

import com.myapp.adventure.utils.ProgressMigration

/**
 * Ф5.5/Ф5.9: state preparation for a manual slot.
 *
 * Kept pure and separated from SAF resolution so tests can verify:
 * - the state is migrated/normalized exactly like autosave;
 * - the source adventure file name is embedded;
 * - `lastAbsoluteAtSave = -1`, so loading a manual save does not deduct
 *   real-world steps walked after the checkpoint was created.
 */
internal object SlotStateFactory {

    fun prepare(state: ProgressData, adventureFileName: String): ProgressData =
        ProgressMigration.migrate(state).copy(
            adventureFileName = adventureFileName,
            lastAbsoluteAtSave = -1,
        )
}
