package com.myapp.adventure.ui.reader

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.databinding.FragmentAdventureReadBinding
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ProgressManager

class AdventureReaderFragment : Fragment() {

    private var _binding: FragmentAdventureReadBinding? = null
    private val binding get() = _binding!!

    private lateinit var adventure: Adventure
    private var adventureFileUri: Uri? = null
    private var currentChapterIndex = 0
    private var currentSceneId = ""
    private var currentStepsDone = 0
    private var goalAbsoluteSteps = -1
    private var startAbsoluteSteps = 0
    private var notificationPermissionGranted = false
    private var activityRecognitionGranted = false

    private val flags = mutableSetOf<String>()
    private val stats = mutableMapOf<String, Int>()
    private val appliedSceneEffects = mutableSetOf<String>()

    private val stepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val absoluteSteps = intent?.getIntExtra(StepCounterConstants.EXTRA_ABSOLUTE_STEPS, 0) ?: 0
            if (goalAbsoluteSteps >= 0) {
                currentStepsDone = (absoluteSteps - startAbsoluteSteps).coerceAtLeast(0)
                val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
                val required = nextChapter?.requiredSteps ?: 0
                if (currentStepsDone >= required) {
                    onStepsCompleted(nextChapter)
                }
                updateStepsProgress()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adventureJson = arguments?.getString("adventure_json")
        if (adventureJson != null) {
            try {
                adventure = Gson().fromJson(adventureJson, Adventure::class.java)
                val uriString = arguments?.getString("adventure_file_uri")
                adventureFileUri = if (uriString != null) Uri.parse(uriString) else null

                val saved = if (adventureFileUri != null) ProgressManager.loadProgress(requireContext(), adventureFileUri!!) else null
                if (saved != null) {
                    currentChapterIndex = saved.currentChapterIndex
                    currentSceneId = saved.currentSceneId
                    currentStepsDone = saved.currentStepsDone
                    goalAbsoluteSteps = saved.goalAbsoluteSteps
                    startAbsoluteSteps = saved.startAbsoluteSteps
                    flags.clear()
                    flags.addAll(saved.flags)
                    stats.clear()
                    stats.putAll(saved.stats)
                    appliedSceneEffects.clear()
                    appliedSceneEffects.addAll(saved.appliedSceneEffects)
                    Logger.log(requireContext(), "Прогресс загружен: глава ${currentChapterIndex+1}, сцена '$currentSceneId', цель: $goalAbsoluteSteps (start=$startAbsoluteSteps)")

                    if (goalAbsoluteSteps >= 0) {
                        val currentAbsolute = StepCounterService.absoluteSteps
                        if (currentAbsolute >= goalAbsoluteSteps) {
                            Logger.log(requireContext(), "Цель уже достигнута при загрузке (абс=$currentAbsolute, цель=$goalAbsoluteSteps)")
                            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
                            if (nextChapter != null) {
                                advanceToNextChapter(nextChapter)
                            }
                        } else {
                            currentStepsDone = (currentAbsolute - startAbsoluteSteps).coerceAtLeast(0)
                        }
                    }
                } else {
                    val initialState = adventure.initialState
                    flags.clear()
                    if (initialState?.flags != null) flags.addAll(initialState.flags)
                    stats.clear()
                    if (initialState?.stats != null) stats.putAll(initialState.stats)
                    appliedSceneEffects.clear()
                    currentChapterIndex = 0
                    currentSceneId = adventure.chapters?.firstOrNull()?.scenes?.firstOrNull()?.id ?: ""
                    goalAbsoluteSteps = -1
                    startAbsoluteSteps = 0
                    Logger.log(requireContext(), "Приключение '${adventure.title}' загружено, глава ${currentChapterIndex+1}")
                }
            } catch (e: Exception) {
                Logger.log(requireContext(), "Ошибка загрузки приключения: ${e.message}")
                Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
                return
            }
        } else {
            Toast.makeText(requireContext(), "Ошибка: нет данных приключения", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureReadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем выбранный шрифт ко всем элементам фрагмента
        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        try {
            checkPermissions()

            binding.btnMenu.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MenuFragment())
                    .addToBackStack(null)
                    .commit()
            }

            binding.btnNextChapter.visibility = View.GONE

            loadCurrentScene()

            requireContext().registerReceiver(
                stepReceiver,
                IntentFilter(StepCounterConstants.ACTION_UPDATE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка в onViewCreated: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка запуска читалки: ${e.message}", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadCurrentScene() {
        val chapters = adventure.chapters.orEmpty()
        val chapter = chapters.getOrNull(currentChapterIndex) ?: run {
            showEnd("Вы прошли все главы!")
            return
        }
        val scenes = chapter.scenes.orEmpty()
        val scene = scenes.find { it.id == currentSceneId } ?: run {
            showEnd("Сцена не найдена")
            return
        }

        binding.tvChapterTitle.text = adventure.title
        binding.tvStoryText.text = scene.text

        if (!appliedSceneEffects.contains(scene.id)) {
            applyEffects(scene.setFlags, scene.removeFlags, scene.modifyStats)
            appliedSceneEffects.add(scene.id)
            Logger.log(requireContext(), "Глава ${currentChapterIndex+1}, сцена '${scene.id}' загружена впервые. Эффекты применены. Здоровье: ${stats["health"] ?: "?"}")
        } else {
            Logger.log(requireContext(), "Глава ${currentChapterIndex+1}, сцена '${scene.id}' загружена (эффекты уже были). Здоровье: ${stats["health"] ?: "?"}")
        }

        val choices = scene.choices.orEmpty()
        if (choices.isEmpty()) {
            if (currentChapterIndex < chapters.size - 1) {
                val nextChapter = chapters[currentChapterIndex + 1]
                if (nextChapter.requiredSteps > 0) {
                    startChapterSteps(nextChapter)
                } else {
                    advanceToNextChapter(nextChapter)
                }
            } else {
                showEnd("Вы прошли все главы!")
            }
        } else {
            updateChoices(choices)
        }
    }

    private fun advanceToNextChapter(nextChapter: com.myapp.adventure.data.Chapter) {
        try {
            currentChapterIndex++
            currentSceneId = nextChapter.scenes?.firstOrNull()?.id ?: ""
            goalAbsoluteSteps = -1
            startAbsoluteSteps = 0
            currentStepsDone = 0
            saveProgress()
            if (_binding != null) {
                loadCurrentScene()
            } else {
                Logger.log(requireContext(), "Представление ещё не создано, глава будет показана при onViewCreated")
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка при продвижении главы: ${e.message}")
        }
    }

    private fun applyEffects(setFlags: List<String>?, removeFlags: List<String>?, modifyStats: Map<String, Int>?) {
        if (setFlags != null) flags.addAll(setFlags)
        if (removeFlags != null) flags.removeAll(removeFlags)
        if (modifyStats != null) {
            modifyStats.forEach { (key, value) ->
                stats[key] = (stats[key] ?: 0) + value
            }
        }
    }

    private fun updateChoices(choices: List<Choice>) {
        val container = binding.choicesContainer
        container.removeAllViews()

        for (choice in choices) {
            if (!isConditionMet(choice.condition)) {
                Logger.log(requireContext(), "Вариант '${choice.text}' скрыт (условие ${choice.condition})")
                continue
            }

            val button = Button(requireContext()).apply {
                text = choice.text
                setOnClickListener {
                    applyEffects(choice.setFlags, choice.removeFlags, choice.modifyStats)
                    Logger.log(requireContext(), "Выбран вариант '${choice.text}', переход на ${choice.nextScene}")
                    currentSceneId = choice.nextScene
                    saveProgress()
                    loadCurrentScene()
                }
            }
            container.addView(button)
        }
    }

    private fun isConditionMet(condition: String?): Boolean {
        if (condition == null) return true
        return try {
            when {
                condition.startsWith("has_") -> {
                    val flag = condition.removePrefix("has_").trim()
                    flags.contains(flag)
                }
                condition.startsWith("!has_") -> {
                    val flag = condition.removePrefix("!has_").trim()
                    !flags.contains(flag)
                }
                condition.contains(">=") -> {
                    val parts = condition.split(">=").map { it.trim() }
                    val statName = parts[0]
                    val threshold = parts[1].toInt()
                    (stats[statName] ?: 0) >= threshold
                }
                condition.contains("<") -> {
                    val parts = condition.split("<").map { it.trim() }
                    val statName = parts[0]
                    val threshold = parts[1].toInt()
                    (stats[statName] ?: 0) < threshold
                }
                else -> true
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка проверки условия '$condition': ${e.message}")
            true
        }
    }

    private fun startChapterSteps(nextChapter: com.myapp.adventure.data.Chapter) {
        val absolute = StepCounterService.absoluteSteps
        goalAbsoluteSteps = absolute + nextChapter.requiredSteps
        startAbsoluteSteps = absolute
        currentStepsDone = 0
        binding.tvStepsRequired.text = "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        saveProgress()
        Logger.log(requireContext(), "Установлена цель: +${nextChapter.requiredSteps} шагов (абсолютная цель: $goalAbsoluteSteps)")
    }

    private fun updateStepsProgress() {
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        binding.tvStepsRequired.text = "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        if (currentStepsDone >= nextChapter.requiredSteps) {
            binding.tvStepsRequired.text = "Цель достигнута! Глава откроется при следующем входе."
        }
    }

    private fun onStepsCompleted(nextChapter: com.myapp.adventure.data.Chapter?) {
        if (nextChapter == null) return
        Logger.log(requireContext(), "Цель достигнута в реальном времени, открытие главы '${nextChapter.title}'")
        binding.tvStepsRequired.visibility = View.GONE
        advanceToNextChapter(nextChapter)
    }

    private fun showEnd(message: String) {
        binding.tvChapterTitle.text = "Конец"
        binding.tvStoryText.text = message
        binding.btnNextChapter.visibility = View.GONE
        binding.tvStepsRequired.visibility = View.GONE

        // Кнопка "Начать заново" на финальном экране
        val container = binding.choicesContainer
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val restartButton = Button(requireContext()).apply {
            text = "Начать заново"
            setOnClickListener {
                onRestartAdventure()
            }
        }
        container.addView(restartButton)
    }

    private fun onRestartAdventure() {
        if (adventureFileUri != null) {
            ProgressManager.deleteProgress(requireContext(), adventureFileUri!!)
            Logger.log(requireContext(), "Прогресс удалён, приключение перезапущено: ${adventure.title}")
        }
        flags.clear()
        stats.clear()
        appliedSceneEffects.clear()
        val initialState = adventure.initialState
        if (initialState?.flags != null) flags.addAll(initialState.flags)
        if (initialState?.stats != null) stats.putAll(initialState.stats)
        currentChapterIndex = 0
        currentSceneId = adventure.chapters?.firstOrNull()?.scenes?.firstOrNull()?.id ?: ""
        goalAbsoluteSteps = -1
        startAbsoluteSteps = 0
        currentStepsDone = 0
        saveProgress()
        loadCurrentScene()
        Toast.makeText(requireContext(), "Приключение начато заново", Toast.LENGTH_SHORT).show()
    }

    private fun saveProgress() {
        if (adventureFileUri == null) return
        val progress = ProgressData(
            adventureFileName = "",
            currentChapterIndex = currentChapterIndex,
            currentSceneId = currentSceneId,
            flags = flags.toSet(),
            stats = stats.toMap(),
            currentStepsDone = currentStepsDone,
            appliedSceneEffects = appliedSceneEffects.toSet(),
            goalAbsoluteSteps = goalAbsoluteSteps,
            startAbsoluteSteps = startAbsoluteSteps
        )
        ProgressManager.saveProgress(requireContext(), adventureFileUri!!, progress)
        Logger.log(requireContext(), "Прогресс сохранён: глава ${currentChapterIndex+1}, цель=$goalAbsoluteSteps")
    }

    private fun checkPermissions() {
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!notificationPermissionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на уведомления. Дайте его в настройках, чтобы шагомер работал в фоне.", Toast.LENGTH_LONG).show()
        }
        if (!activityRecognitionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на физическую активность. Шаги не будут считаться.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveProgress()
        try {
            requireContext().unregisterReceiver(stepReceiver)
        } catch (_: Exception) { }
        _binding = null
    }
}