package com.myapp.adventure.ui.reader

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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

    private var previewMode = false
    private var previewStartSceneId = ""
    private var previewStartChapterIndex = 0

    /** Активен ли сейчас экран шагового шлюза (только в preview-режиме). */
    private var isWaitingForSteps = false

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
        if (adventureJson == null) {
            Toast.makeText(requireContext(), "Ошибка: нет данных приключения", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        try {
            adventure = Gson().fromJson(adventureJson, Adventure::class.java)
            previewMode = arguments?.getBoolean("preview_mode", false) ?: false

            if (previewMode) {
                initPreviewState()
            } else {
                initNormalState()
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка загрузки приключения: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun initPreviewState() {
        previewStartSceneId = arguments?.getString("preview_scene_id") ?: ""
        previewStartChapterIndex = arguments?.getInt("preview_chapter_index", 0) ?: 0
        adventureFileUri = null

        val initialState = adventure.initialState
        flags.clear()
        if (initialState?.flags != null) flags.addAll(initialState.flags)
        stats.clear()
        if (initialState?.stats != null) stats.putAll(initialState.stats)
        appliedSceneEffects.clear()

        currentChapterIndex = previewStartChapterIndex
        currentSceneId = previewStartSceneId
        goalAbsoluteSteps = -1
        startAbsoluteSteps = 0
        currentStepsDone = 0
        isWaitingForSteps = false

        Logger.log(
            requireContext(),
            "Предпросмотр: глава ${previewStartChapterIndex + 1}, сцена '$previewStartSceneId'"
        )
    }

    private fun initNormalState() {
        val uriString = arguments?.getString("adventure_file_uri")
        adventureFileUri = if (uriString != null) Uri.parse(uriString) else null

        val saved = if (adventureFileUri != null) {
            ProgressManager.loadProgress(requireContext(), adventureFileUri!!)
        } else null

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
            Logger.log(
                requireContext(),
                "Прогресс загружен: глава ${currentChapterIndex + 1}, сцена '$currentSceneId', цель: $goalAbsoluteSteps (start=$startAbsoluteSteps)"
            )

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
            Logger.log(requireContext(), "Приключение '${adventure.title}' загружено, глава ${currentChapterIndex + 1}")
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

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        try {
            if (!previewMode) {
                checkPermissions()
            }

            if (previewMode) {
                binding.btnMenu.text = "Назад"
                binding.btnMenu.setOnClickListener {
                    parentFragmentManager.popBackStack()
                }
                binding.btnDebug.visibility = View.VISIBLE
                binding.btnDebug.setOnClickListener { showDebugPanel() }
            } else {
                binding.btnMenu.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MenuFragment())
                        .addToBackStack(null)
                        .commit()
                }
                binding.btnDebug.visibility = View.GONE
            }

            binding.btnNextChapter.visibility = View.GONE

            loadCurrentScene()

            if (!previewMode) {
                requireContext().registerReceiver(
                    stepReceiver,
                    IntentFilter(StepCounterConstants.ACTION_UPDATE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            }
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

        binding.tvChapterTitle.text = if (previewMode) {
            "▶ Предпросмотр — ${adventure.title}"
        } else {
            adventure.title
        }
        binding.tvStoryText.text = scene.text

        if (!appliedSceneEffects.contains(scene.id)) {
            applyEffects(scene.setFlags, scene.removeFlags, scene.modifyStats)
            appliedSceneEffects.add(scene.id)
            Logger.log(requireContext(), "Глава ${currentChapterIndex + 1}, сцена '${scene.id}' загружена впервые. Эффекты применены. Здоровье: ${stats["health"] ?: "?"}")
        } else {
            Logger.log(requireContext(), "Глава ${currentChapterIndex + 1}, сцена '${scene.id}' загружена (эффекты уже были). Здоровье: ${stats["health"] ?: "?"}")
        }

        val choices = scene.choices.orEmpty()
        if (choices.isEmpty()) {
            if (currentChapterIndex < chapters.size - 1) {
                val nextChapter = chapters[currentChapterIndex + 1]
                if (nextChapter.requiredSteps > 0) {
                    // В preview показываем реальный экран шлюза,
                    // чтобы автор видел UI и цифры как в обычной читалке
                    if (previewMode) {
                        startChapterStepsInPreview(nextChapter)
                    } else {
                        startChapterSteps(nextChapter)
                    }
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
            isWaitingForSteps = false
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

    /**
     * Обновляет список кнопок-выборов.
     *
     * В обычной читалке: выборы с невыполненными условиями скрываются.
     * В предпросмотре: показываются все, но невыполненные — серые, неактивные,
     * с пометкой 🔒 и текстом условия.
     */
    private fun updateChoices(choices: List<Choice>) {
        val container = binding.choicesContainer
        container.removeAllViews()

        for (choice in choices) {
            val conditionMet = isConditionMet(choice.condition)

            if (!previewMode && !conditionMet) {
                Logger.log(requireContext(), "Вариант '${choice.text}' скрыт (условие ${choice.condition})")
                continue
            }

            val button = Button(requireContext()).apply {
                if (previewMode && !conditionMet) {
                    val conditionLabel = choice.condition ?: ""
                    text = "${choice.text}  🔒 ($conditionLabel)"
                    isEnabled = false
                    setTextColor(Color.parseColor("#808080"))
                    alpha = 0.6f
                    Logger.log(requireContext(), "Вариант '${choice.text}' показан как неактивный (условие '$conditionLabel' не выполнено)")
                } else {
                    text = choice.text
                    setOnClickListener {
                        applyEffects(choice.setFlags, choice.removeFlags, choice.modifyStats)
                        Logger.log(requireContext(), "Выбран вариант '${choice.text}', переход на ${choice.nextScene}")
                        currentSceneId = choice.nextScene
                        saveProgress()
                        loadCurrentScene()
                    }
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

    // ---------- Шаговые шлюзы ----------

    /** Обычная читалка: реальный шагомер, абсолютные значения. */
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

    /** Preview: шагомера нет, показываем экран шлюза с нулём. */
    private fun startChapterStepsInPreview(nextChapter: com.myapp.adventure.data.Chapter) {
        isWaitingForSteps = true
        goalAbsoluteSteps = -1
        startAbsoluteSteps = 0
        currentStepsDone = 0
        showStepsGate(nextChapter)
        Logger.log(requireContext(), "Preview-шлюз: глава «${nextChapter.title}», требуется ${nextChapter.requiredSteps} шагов")
    }

    /** Рисует текст шлюза и скрывает кнопки выборов. */
    private fun showStepsGate(nextChapter: com.myapp.adventure.data.Chapter) {
        binding.tvStepsRequired.text =
            "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        binding.choicesContainer.removeAllViews()
    }

    /** Обычная читалка: обновляет текст шлюза по мере поступления шагов. */
    private fun updateStepsProgress() {
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        binding.tvStepsRequired.text = "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        if (currentStepsDone >= nextChapter.requiredSteps) {
            binding.tvStepsRequired.text = "Цель достигнута! Глава откроется при следующем входе."
        }
    }

    /** Preview: симулирует ввод шагов из отладочной панели. */
    private fun setDebugSteps(value: Int) {
        currentStepsDone = value.coerceAtLeast(0)
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        val required = nextChapter.requiredSteps
        if (required == 0) return

        Logger.log(requireContext(), "Симуляция шагов: пройдено $currentStepsDone из $required")

        if (currentStepsDone >= required) {
            onStepsCompleted(nextChapter)
        } else {
            showStepsGate(nextChapter)
        }
    }

    private fun onStepsCompleted(nextChapter: com.myapp.adventure.data.Chapter?) {
        if (nextChapter == null) return
        Logger.log(requireContext(), "Цель достигнута, открытие главы '${nextChapter.title}'")
        isWaitingForSteps = false
        binding.tvStepsRequired.visibility = View.GONE
        advanceToNextChapter(nextChapter)
    }

    // ---------- Конец истории ----------

    private fun showEnd(message: String) {
        binding.tvChapterTitle.text = if (previewMode) "▶ Конец предпросмотра" else "Конец"
        binding.tvStoryText.text = message
        binding.btnNextChapter.visibility = View.GONE
        binding.tvStepsRequired.visibility = View.GONE

        val container = binding.choicesContainer
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val restartButton = Button(requireContext()).apply {
            text = if (previewMode) "Вернуться в редактор" else "Начать заново"
            setOnClickListener {
                if (previewMode) {
                    parentFragmentManager.popBackStack()
                } else {
                    onRestartAdventure()
                }
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

        if (previewMode) {
            currentChapterIndex = previewStartChapterIndex
            currentSceneId = previewStartSceneId
        } else {
            currentChapterIndex = 0
            currentSceneId = adventure.chapters?.firstOrNull()?.scenes?.firstOrNull()?.id ?: ""
        }
        goalAbsoluteSteps = -1
        startAbsoluteSteps = 0
        currentStepsDone = 0
        isWaitingForSteps = false
        saveProgress()
        loadCurrentScene()
        Toast.makeText(requireContext(), "Приключение начато заново", Toast.LENGTH_SHORT).show()
    }

    private fun saveProgress() {
        if (previewMode) return
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
        Logger.log(requireContext(), "Прогресс сохранён: глава ${currentChapterIndex + 1}, цель=$goalAbsoluteSteps")
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

    // ---------- Отладочная панель ----------

    private fun showDebugPanel() {
        if (!previewMode) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_panel, null)

        val tvSceneInfo = dialogView.findViewById<TextView>(R.id.tv_debug_scene_info)
        val containerStats = dialogView.findViewById<LinearLayout>(R.id.container_stats)
        val containerFlags = dialogView.findViewById<LinearLayout>(R.id.container_flags)
        val btnAddStat = dialogView.findViewById<Button>(R.id.btn_add_stat)
        val btnAddFlag = dialogView.findViewById<Button>(R.id.btn_add_flag)
        val btnReset = dialogView.findViewById<Button>(R.id.btn_reset)

        val sectionSteps = dialogView.findViewById<LinearLayout>(R.id.section_steps)
        val tvStepsStatus = dialogView.findViewById<TextView>(R.id.tv_steps_status)
        val etStepsValue = dialogView.findViewById<EditText>(R.id.et_steps_value)
        val btnApplySteps = dialogView.findViewById<Button>(R.id.btn_apply_steps)
        val btnOpenChapterNow = dialogView.findViewById<Button>(R.id.btn_open_chapter_now)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("🐞 Отладка")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .create()

        // Первичное заполнение поля шагов (только при первом открытии панели,
        // чтобы не перезаписывать ручной ввод при каждом refresh)
        val nextChapterInitial = adventure.chapters?.getOrNull(currentChapterIndex + 1)
        if (isWaitingForSteps && nextChapterInitial != null && nextChapterInitial.requiredSteps > 0) {
            etStepsValue.setText(currentStepsDone.toString())
        }

        fun refreshStepsSection() {
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
            if (isWaitingForSteps && nextChapter != null && nextChapter.requiredSteps > 0) {
                sectionSteps.visibility = View.VISIBLE
                tvStepsStatus.text =
                    "Глава «${nextChapter.title}»: пройдено $currentStepsDone из ${nextChapter.requiredSteps}"
            } else {
                sectionSteps.visibility = View.GONE
            }
        }

        fun refresh() {
            tvSceneInfo.text = "Текущая сцена: $currentSceneId"

            containerStats.removeAllViews()
            if (stats.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = "(нет статов)"
                    setTextColor(Color.parseColor("#9E9E9E"))
                    textSize = 12f
                }
                containerStats.addView(tv)
            } else {
                for ((name, value) in stats.toSortedMap()) {
                    val rowView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_debug_stat, containerStats, false)
                    val tvName = rowView.findViewById<TextView>(R.id.tv_stat_name)
                    val tvValue = rowView.findViewById<TextView>(R.id.tv_stat_value)
                    val btnEdit = rowView.findViewById<View>(R.id.btn_edit_stat)
                    val btnDelete = rowView.findViewById<View>(R.id.btn_delete_stat)

                    tvName.text = name
                    tvValue.text = value.toString()

                    btnEdit.setOnClickListener {
                        showEditStatDialog(name, value) { newValue ->
                            stats[name] = newValue
                            loadCurrentScene()
                            refresh()
                        }
                    }
                    btnDelete.setOnClickListener {
                        stats.remove(name)
                        loadCurrentScene()
                        refresh()
                    }

                    containerStats.addView(rowView)
                }
            }

            containerFlags.removeAllViews()
            if (flags.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = "(нет флагов)"
                    setTextColor(Color.parseColor("#9E9E9E"))
                    textSize = 12f
                }
                containerFlags.addView(tv)
            } else {
                for (flag in flags.toSortedSet()) {
                    val rowView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_debug_flag, containerFlags, false)
                    val tvName = rowView.findViewById<TextView>(R.id.tv_flag_name)
                    val btnDelete = rowView.findViewById<View>(R.id.btn_delete_flag)

                    tvName.text = flag

                    btnDelete.setOnClickListener {
                        flags.remove(flag)
                        loadCurrentScene()
                        refresh()
                    }

                    containerFlags.addView(rowView)
                }
            }

            refreshStepsSection()
        }

        btnAddStat.setOnClickListener {
            showAddStatDialog { name, value ->
                stats[name] = value
                loadCurrentScene()
                refresh()
            }
        }

        btnAddFlag.setOnClickListener {
            showAddFlagDialog { name ->
                flags.add(name)
                loadCurrentScene()
                refresh()
            }
        }

        btnApplySteps.setOnClickListener {
            val value = etStepsValue.text.toString().toIntOrNull()
            if (value == null || value < 0) {
                Toast.makeText(requireContext(), "Введите целое число ≥ 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setDebugSteps(value)
            refresh()
        }

        btnOpenChapterNow.setOnClickListener {
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
            if (nextChapter == null || nextChapter.requiredSteps == 0) {
                Toast.makeText(requireContext(), "Шлюз не активен", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onStepsCompleted(nextChapter)
            refresh()
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Сбросить?")
                .setMessage("Сбросить флаги и статы к начальному состоянию?")
                .setPositiveButton("Сбросить") { _, _ ->
                    flags.clear()
                    stats.clear()
                    val initialState = adventure.initialState
                    if (initialState?.flags != null) flags.addAll(initialState.flags)
                    if (initialState?.stats != null) stats.putAll(initialState.stats)
                    loadCurrentScene()
                    refresh()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        refresh()
        dialog.show()
    }

    private fun showEditStatDialog(
        statName: String,
        currentValue: Int,
        onResult: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_stat, null)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_stat_value)
        val tvName = dialogView.findViewById<TextView>(R.id.tv_debug_stat_name)

        tvName.text = statName
        etValue.setText(currentValue.toString())
        etValue.setSelection(etValue.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить значение")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val v = etValue.text.toString().toIntOrNull() ?: return@setPositiveButton
                onResult(v)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddStatDialog(onResult: (String, Int) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_add, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_debug_name)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_value)

        etValue.visibility = View.VISIBLE
        etValue.setText("0")

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить стат")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().toIntOrNull() ?: 0
                if (name.isNotEmpty()) onResult(name, value)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddFlagDialog(onResult: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_add, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_debug_name)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_value)

        etValue.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить флаг")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) onResult(name)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveProgress()
        if (!previewMode) {
            try {
                requireContext().unregisterReceiver(stepReceiver)
            } catch (_: Exception) { }
        }
        _binding = null
    }
}