package com.myapp.adventure.ui.list

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.utils.AdventureImportHelper
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One entry in the library list: parsed IR + the file it came from. */
data class AdventureListItem(
    val adventure: Adventure,
    val uri: Uri,
)

data class AdventureListUiState(
    val isLoading: Boolean = true,
    val items: List<AdventureListItem> = emptyList(),
)

sealed interface AdventureListEvent {
    data class ShowToast(val message: String, val longDuration: Boolean = false) : AdventureListEvent
}

class AdventureListViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AdventureListUiState())
    val uiState: StateFlow<AdventureListUiState> = _uiState.asStateFlow()

    private val _events = Channel<AdventureListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { loadAdventures() }
    }

    /** Reload the library from the SAF folder. */
    suspend fun loadAdventures() {
        _uiState.update { it.copy(isLoading = true) }
        val result = withContext(Dispatchers.IO) { loadAdventuresFromDisk() }
        _uiState.update { it.copy(isLoading = false, items = result.items) }
        result.errorMessage?.let { message ->
            _events.send(AdventureListEvent.ShowToast(message, longDuration = result.isError))
        }
    }

    /** Import an adventure from a SAF URI, then reload the library. */
    fun onImportFile(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                withContext(Dispatchers.IO) {
                    AdventureImportHelper.importAdventure(app, uri)
                }
            } catch (e: Exception) {
                Logger.log(app, "Import failed: ${e.message}")
                _events.send(
                    AdventureListEvent.ShowToast(
                        app.getString(R.string.library_toast_folder_access_error_fmt, e.message),
                        longDuration = true,
                    )
                )
            }
            loadAdventures()
        }
    }

    // ------------------------------------------------------------------
    // Disk access (runs on Dispatchers.IO from loadAdventures)
    // ------------------------------------------------------------------

    private data class LoadResult(
        val items: List<AdventureListItem>,
        val errorMessage: String? = null,
        val isError: Boolean = false,
    )

    private fun loadAdventuresFromDisk(): LoadResult {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("adventure_prefs", 0)
        val folderUriString = prefs.getString("adventure_folder_uri", null)
            ?: return LoadResult(
                items = emptyList(),
                errorMessage = app.getString(R.string.library_toast_folder_not_selected),
            )

        return try {
            val folderUri = Uri.parse(folderUriString)
            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/Adventures",
            )
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                adventuresDirUri,
                DocumentsContract.getDocumentId(adventuresDirUri),
            )

            val items = mutableListOf<AdventureListItem>()
            app.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    val mimeType = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )
                    if (mimeType == "application/json") {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                            adventuresDirUri, documentId
                        )
                        val adventure = parseAdventureFromUri(app, fileUri)
                        if (adventure != null) {
                            items.add(AdventureListItem(adventure, fileUri))
                        }
                    }
                }
            }
            Logger.log(app, "Загружено приключений: ${items.size}")
            LoadResult(items = items)
        } catch (e: Exception) {
            Logger.log(app, "Ошибка загрузки списка: ${e.message}")
            LoadResult(
                items = emptyList(),
                errorMessage = app.getString(
                    R.string.library_toast_folder_access_error_fmt, e.message
                ),
                isError = true,
            )
        }
    }

    /**
     * Parses one adventure file. Files that fail to parse are silently
     * skipped (logged, not shown) — same as the original Fragment.
     */
    private fun parseAdventureFromUri(app: Application, uri: Uri): Adventure? {
        return try {
            val json = app.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return null
            when (val result = JsonToIr.parse(json)) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> {
                    val reason = result.errors.firstOrNull()?.message
                        ?: result.typeErrors.firstOrNull()?.message
                        ?: "unknown error"
                    Logger.log(app, "Skip '${uri.lastPathSegment}': $reason")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.log(app, "Ошибка чтения файла: ${e.message}")
            null
        }
    }
}