package com.myapp.adventure.ui.setup

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen the user is currently on. */
enum class SetupStep {
    /** Initial state: checking saved folder (system splash usually covers this). */
    LOADING,
    FOLDER,
    NOTIFICATIONS,
    ACTIVITY,
    DONE,
}

sealed interface SetupEvent {
    data object NavigateToList : SetupEvent
    data class ShowToast(val message: String, val longDuration: Boolean = false) : SetupEvent
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val _step = MutableStateFlow(SetupStep.LOADING)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _events = Channel<SetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("adventure_prefs", 0)
            val savedUri = prefs.getString("adventure_folder_uri", null)

            // SAF query is I/O; keep it off the main thread so the first frame
            // (LOADING) is drawn immediately and doesn't stall on the query.
            val valid = savedUri != null && withContext(Dispatchers.IO) {
                isFolderValid(app, Uri.parse(savedUri))
            }

            if (valid && savedUri != null) {
                Logger.init(app, savedUri)
                ProgressManager.warmUp(app)
                _step.value = SetupStep.DONE
                _events.send(SetupEvent.NavigateToList)
            } else {
                if (savedUri != null) {
                    prefs.edit().remove("adventure_folder_uri").apply()
                }
                _step.value = SetupStep.FOLDER
            }
        }
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                _events.send(
                    SetupEvent.ShowToast(
                        getApplication<Application>().getString(R.string.setup_toast_choose_folder)
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                app.contentResolver.takePersistableUriPermission(uri, takeFlags)

                app.getSharedPreferences("adventure_prefs", 0)
                    .edit()
                    .putString("adventure_folder_uri", uri.toString())
                    .apply()

                Logger.init(app, uri.toString())
                Logger.log(app, "Папка выбрана: $uri")

                createSubFolders(app, uri)

                _events.send(SetupEvent.ShowToast(app.getString(R.string.setup_toast_folder_selected)))
                // Folder is ready; proceed to the permissions walkthrough.
                _step.value = SetupStep.NOTIFICATIONS
            } catch (e: Exception) {
                Logger.log(app, "Ошибка при выборе папки: ${e.message}")
                _events.send(
                    SetupEvent.ShowToast(
                        app.getString(R.string.setup_toast_error_fmt, e.message),
                        longDuration = true,
                    )
                )
            }
        }
    }

    /** Called after the notifications permission dialog is dismissed. */
    fun onNotificationsResolved() {
        _step.value = SetupStep.ACTIVITY
    }

    /**
     * Called after the activity-recognition permission dialog is dismissed.
     *
     * @param activityRecognitionGranted whether ACTIVITY_RECOGNITION is now granted.
     * @param useHealthConnect user's choice of step source (false = phone sensor).
     *
     * The step counter foreground service is started only when the `health`
     * foreground-service prerequisite is satisfied; otherwise the app runs in
     * a degraded (no step counting) mode, and the user can grant the permission
     * later from Settings.
     */
    fun onActivityResolved(activityRecognitionGranted: Boolean) {
        val app = getApplication<Application>()
        if (activityRecognitionGranted) {
            startStepCounterService(app)
        } else {
            Logger.log(app, "Step counter not started: ACTIVITY_RECOGNITION denied")
        }
        _step.value = SetupStep.DONE
        viewModelScope.launch { _events.send(SetupEvent.NavigateToList) }
    }

    // ------------------------------------------------------------------
    // Business logic (unchanged)
    // ------------------------------------------------------------------

    private fun isFolderValid(app: Application, folderUri: Uri): Boolean = try {
        val docId = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
        app.contentResolver.query(childrenUri, null, null, null, null)?.close()
        true
    } catch (_: Exception) {
        false
    }

    private fun createSubFolders(app: Application, parentUri: Uri) {
        val parentDocId = DocumentsContract.getTreeDocumentId(parentUri)
        createSubFolder(app, parentUri, parentDocId, "Adventures")
        createSubFolder(app, parentUri, parentDocId, "NewAdventures")
        copyTestAdventure(app, parentUri, "test_adventure.json", R.raw.test_adventure)
    }

    private fun createSubFolder(
        app: Application,
        parentUri: Uri,
        parentDocId: String,
        name: String,
    ) {
        try {
            DocumentsContract.createDocument(
                app.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(parentUri, parentDocId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
            Logger.log(app, "Папка $name создана")
        } catch (e: Exception) {
            Logger.log(app, "Папка $name уже существует или ошибка: ${e.message}")
        }
    }

    private fun copyTestAdventure(
        app: Application,
        parentUri: Uri,
        fileName: String,
        rawResId: Int,
    ) {
        try {
            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                parentUri,
                DocumentsContract.getTreeDocumentId(parentUri) + "/Adventures",
            )
            if (getFileNamesInFolder(app, adventuresDirUri).contains(fileName)) return
            val fileUri = DocumentsContract.createDocument(
                app.contentResolver, adventuresDirUri, "application/json", fileName,
            ) ?: return
            val jsonString = app.resources.openRawResource(rawResId)
                .bufferedReader().use { it.readText() }
            app.contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(jsonString.toByteArray())
            }
            Logger.log(app, "Тестовое приключение $fileName скопировано")
        } catch (e: Exception) {
            Logger.log(app, "Ошибка копирования $fileName: ${e.message}")
        }
    }

    private fun getFileNamesInFolder(app: Application, folderUri: Uri): List<String> {
        val names = mutableListOf<String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri),
        )
        app.contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
            }
        }
        return names
    }

    private fun startStepCounterService(app: Application) {
        if (!StepCounterService.isRunning) {
            val intent = Intent(app, StepCounterService::class.java).apply {
                action = StepCounterConstants.ACTION_START
            }
            app.startForegroundService(intent)
            Logger.log(app, "Сервис шагомера запущен из SetupViewModel")
        }
    }
}