package com.myapp.adventure.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentSetupBinding
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.ui.list.AdventureListFragment
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ProgressManager
import kotlinx.coroutines.launch

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private var permissionsChecked = false

    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                requireContext().getSharedPreferences("adventure_prefs", 0)
                    .edit()
                    .putString("adventure_folder_uri", uri.toString())
                    .apply()

                Logger.init(requireContext(), uri.toString())
                Logger.log(requireContext(), "Папка выбрана: $uri")

                createSubFolders(uri)

                Toast.makeText(requireContext(), "Папка выбрана!", Toast.LENGTH_SHORT).show()

                // Запускаем постоянный шагомер, если ещё не работает
                startStepCounterService()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AdventureListFragment())
                    .commit()
            } catch (e: Exception) {
                Logger.log(requireContext(), "Ошибка при выборе папки: ${e.message}")
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(requireContext(), "Нужно выбрать папку для продолжения", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Уведомления отключены. Шагомер не сможет работать в фоне.", Toast.LENGTH_LONG).show()
            }
            requestActivityRecognition()
        }

    private val requestActivityRecognitionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Разрешение на физическую активность не дано. Шаги не будут считаться.", Toast.LENGTH_LONG).show()
            }
            permissionsChecked = true
            // После получения разрешений можно запускать шагомер
            startStepCounterService()
            updateUI()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
        val savedUri = prefs.getString("adventure_folder_uri", null)

        // Если папка уже выбрана и валидна – сразу в список и запускаем шагомер
        if (savedUri != null && isFolderValid(Uri.parse(savedUri))) {
            Logger.init(requireContext(), savedUri)
            startStepCounterService()
            viewLifecycleOwner.lifecycleScope.launch {
                ProgressManager.warmUp(requireContext().applicationContext)
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdventureListFragment())
                .commit()
            return
        } else if (savedUri != null) {
            prefs.edit().remove("adventure_folder_uri").apply()
        }

        binding.btnSelectFolder.setOnClickListener {
            if (permissionsChecked) {
                selectFolderLauncher.launch(null)
            } else {
                checkAndRequestPermissions()
            }
        }

        // Запрашиваем разрешения сразу, если они не были запрошены
        if (!permissionsChecked) {
            checkAndRequestPermissions()
        }
        // Start periodic polling (no-op if HC is disabled — sensor writes directly)
        com.myapp.adventure.step.StepSourceManager.start(requireContext())
    }

    private fun isFolderValid(folderUri: Uri): Boolean {
        return try {
            val contentResolver = requireContext().contentResolver
            val docId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
            val cursor = contentResolver.query(childrenUri, null, null, null, null)
            cursor?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkAndRequestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        Toast.makeText(requireContext(), "Разрешение на уведомления нужно, чтобы шагомер работал в фоне.", Toast.LENGTH_LONG).show()
                    }
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            requestActivityRecognition()
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка при запросе разрешений: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestActivityRecognition() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                        Toast.makeText(requireContext(), "Доступ к физической активности нужен для подсчёта шагов.", Toast.LENGTH_LONG).show()
                    }
                    requestActivityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    return
                }
            }
            permissionsChecked = true
            startStepCounterService()
            updateUI()
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка при запросе активности: ${e.message}")
        }
    }

    private fun updateUI() {
        if (_binding != null) {
            binding.btnSelectFolder.isEnabled = true
        }
    }

    private fun startStepCounterService() {
        if (!StepCounterService.isRunning) {
            val intent = Intent(requireContext(), StepCounterService::class.java).apply {
                action = StepCounterConstants.ACTION_START
            }
            requireContext().startForegroundService(intent)
            Logger.log(requireContext(), "Сервис шагомера запущен из SetupFragment")
        }
    }

    // ... методы createSubFolders, copyTestAdventure и т.д. – без изменений

    private fun createSubFolders(parentUri: Uri) {
        val contentResolver = requireContext().contentResolver
        val parentDocId = DocumentsContract.getTreeDocumentId(parentUri)

        try {
            DocumentsContract.createDocument(
                contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(parentUri, parentDocId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                "Adventures"
            )
            Logger.log(requireContext(), "Папка Adventures создана")
        } catch (e: Exception) {
            Logger.log(requireContext(), "Папка Adventures уже существует или ошибка: ${e.message}")
        }

        try {
            DocumentsContract.createDocument(
                contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(parentUri, parentDocId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                "NewAdventures"
            )
            Logger.log(requireContext(), "Папка NewAdventures создана")
        } catch (e: Exception) {
            Logger.log(requireContext(), "Папка NewAdventures уже существует или ошибка: ${e.message}")
        }

        copyTestAdventureIfNeeded(parentUri)
    }

    private fun copyTestAdventureIfNeeded(parentUri: Uri) {
        copyTestAdventure(parentUri, "test_adventure.json", R.raw.test_adventure)
    }

    private fun copyTestAdventure(parentUri: Uri, fileName: String, rawResId: Int) {
        try {
            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                parentUri,
                DocumentsContract.getTreeDocumentId(parentUri) + "/Adventures"
            )
            val existingFiles = getFileNamesInFolder(adventuresDirUri)
            if (existingFiles.contains(fileName)) return
            val fileUri = DocumentsContract.createDocument(
                requireContext().contentResolver,
                adventuresDirUri,
                "application/json",
                fileName
            )
            if (fileUri != null) {
                val jsonString = resources.openRawResource(rawResId)
                    .bufferedReader().use { it.readText() }
                requireContext().contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Logger.log(requireContext(), "Тестовое приключение $fileName скопировано")
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка копирования $fileName: ${e.message}")
        }
    }

    private fun getFileNamesInFolder(folderUri: Uri): List<String> {
        val names = mutableListOf<String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri)
        )
        val cursor = requireContext().contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                names.add(name)
            }
        }
        return names
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}