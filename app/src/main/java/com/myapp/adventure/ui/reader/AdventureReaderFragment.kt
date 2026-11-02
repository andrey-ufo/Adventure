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
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.databinding.FragmentAdventureReadBinding
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.ui.SettingsFragment
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

    private var isWaitingForSteps = false

    // JOURNAL-флаги, порядок добавления (новые в конце)
    private val flags = mutableListOf<String>()

    // Значения HEALTH и ITEM (и числовых JOURNAL)
    private val stats = mutableMapOf<String, Int>()

    // ITEM-метки, порядок получения (новые в конце)
    private val itemOrder = mutableListOf<String>()

    private val appliedSceneEffects = mutableSetOf<String>()

    // Реестр меток из adventure.tags для быстрого доступа
    private val tagRegistry: Map<String, TagDefinition>
        get() = adventure.tags.orEmpty().associateBy { it.name }

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

    // ---------- Инициализация ----------

    private fun addFlagIfMissing(flag: String) {
        if (!flags.contains(flag)) flags.add(flag)
    }

    /** Возвращает категорию метки из реестра, либо JOURNAL по умолчанию. */
    private fun getCategory(name: String): TagCategory {
        return tagRegistry[name]?.category ?: TagCategory.JOURNAL
    }

    /** Возвращает отображаемое имя метки. */
    private fun getDisplayName(name: String): String {
        val def = tagRegistry[name]
        return def?.displayName?.ifBlank { name } ?: name
    }

    /** Видима ли метка в HUD. */
    private fun isVisible(name: String): Boolean {
        return tagRegistry[name]?.visible ?: false
    }

    /** Регистрирует получение ITEM-метки в itemOrder. */
    private fun registerItem(name: String, newCount: Int) {
        if (getCategory(name) != TagCategory.ITEM) return
        if (newCount > 0 && !itemOrder.contains(name)) {
            itemOrder.add(name)
        } else if (newCount <= 0) {
            itemOrder.remove(name)
        }
    }

    /** Восстанавливает itemOrder после загрузки stats (порядок = порядок tags). */
    private fun rebuildItemOrder() {
        itemOrder.clear()
        adventure.tags?.filter { it.category == TagCategory.ITEM }?.forEach { def ->
            if ((stats[def.name] ?: 0) > 0) {
                itemOrder.add(def.name)
            }
        }
    }

    private fun initPreviewState() {
        previewStartSceneId = arguments?.getString("preview_scene_id") ?: ""
        previewStartChapterIndex = arguments?.getInt("preview_chapter_index", 0) ?: 0
        adventureFileUri = null

        val initialState = adventure.initialState
        flags.clear()
        initialState?.flags?.forEach { addFlagIfMissing(it) }
        stats.clear()
        if (initialState?.stats != null) stats.putAll(initialState.stats)
        appliedSceneEffects.clear()
        rebuildItemOrder()

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
            saved.flags.forEach { addFlagIfMissing(it) }
            stats.clear()
            stats.putAll(saved.stats)
            appliedSceneEffects.clear()
            appliedSceneEffects.addAll(saved.appliedSceneEffects)
            itemOrder.clear()
            if (saved.itemOrder.isNotEmpty()) {
                // Восстанавливаем сохранённый порядок, оставляя только те предметы,
                // что реально есть в stats с количеством > 0
                for (name in saved.itemOrder) {
                    if ((stats[name] ?: 0) > 0) itemOrder.add(name)
                }
            } else {
                // Fallback для старых сохранений без itemOrder
                rebuildItemOrder()
            }
            Logger.log(
                requireContext(),
                "Прогресс загружен: глава ${currentChapterIndex + 1}, сцена '$currentSceneId', цель: $goalAbsoluteSteps"
            )

            if (goalAbsoluteSteps >= 0) {
                val currentAbsolute = StepCounterService.absoluteSteps
                if (currentAbsolute >= goalAbsoluteSteps) {
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
            initialState?.flags?.forEach { addFlagIfMissing(it) }
            stats.clear()
            if (initialState?.stats != null) stats.putAll(initialState.stats)
            appliedSceneEffects.clear()
            rebuildItemOrder()
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
                binding.btnMenu.setOnClickListener { showReaderMenuDialog() }
                binding.btnDebug.visibility = View.GONE
            }

            // HUD
            binding.btnHealth.setOnClickListener { showHealthDialog() }
            binding.btnInventory.setOnClickListener { showInventoryDialog() }
            binding.btnJournal.setOnClickListener { showJournalDialog() }

            binding.btnNextChapter.visibility = View.GONE

            loadCurrentScene()
            updateHud()

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

    // ---------- HUD ----------

    private fun updateHud() {
        if (_binding == null) return

        // Здоровье: health по имени или первый HEALTH из tags
        val healthDef = adventure.tags?.firstOrNull { it.name == "health" && it.category == TagCategory.HEALTH }
            ?: adventure.tags?.firstOrNull { it.category == TagCategory.HEALTH }
        val healthValue = healthDef?.let { stats[it.name] ?: 0 } ?: 0
        binding.btnHealth.text = "❤️ $healthValue"

        // Рюкзак: количество ITEM-меток с > 0
        val itemCount = itemOrder.count { (stats[it] ?: 0) > 0 }
        binding.btnInventory.text = "🎒 $itemCount"

        // Журнал: количество видимых JOURNAL-меток
        val journalCount = getJournalItems().size
        binding.btnJournal.text = "📖 $journalCount"
    }

    /** Все HEALTH-метки из реестра: (displayName, value). */
    private fun getHealthItems(): List<Pair<String, Int>> {
        return adventure.tags.orEmpty()
            .filter { it.category == TagCategory.HEALTH }
            .map { def ->
                val display = def.displayName.ifBlank { def.name }
                display to (stats[def.name] ?: 0)
            }
    }

    /** ITEM-метки с количеством > 0, новые сверху. */
    private fun getInventoryItems(): List<Pair<String, Int>> {
        return itemOrder.asReversed()
            .filter { (stats[it] ?: 0) > 0 }
            .map { name -> getDisplayName(name) to (stats[name] ?: 0) }
    }

    /** Видимые JOURNAL-метки (флаги и числовые), новые сверху. */
    private fun getJournalItems(): List<String> {
        val result = mutableListOf<String>()

        // Флаги — обратный порядок, чтобы новые были сверху
        for (name in flags.asReversed()) {
            if (isVisible(name) && getCategory(name) == TagCategory.JOURNAL) {
                result.add(getDisplayName(name))
            }
        }

        // Числовые JOURNAL — в порядке объявления в tags
        adventure.tags.orEmpty()
            .filter { it.category == TagCategory.JOURNAL && it.visible }
            .forEach { def ->
                if (!flags.contains(def.name) && (stats[def.name] ?: 0) != 0) {
                    val display = def.displayName.ifBlank { def.name }
                    result.add("$display: ${stats[def.name]}")
                }
            }

        return result
    }

    // ---------- Диалоги HUD ----------

    private fun showHealthDialog() {
        val items = getHealthItems()
        showListDialog("❤️ Здоровье", items)
    }

    private fun showInventoryDialog() {
        val items = getInventoryItems()
        showListDialog("🎒 Рюкзак", items)
    }

    private fun showJournalDialog() {
        val items = getJournalItems().map { it to null }
        showListDialog("📖 Журнал", items)
    }

    private fun showListDialog(title: String, items: List<Pair<String, Int?>>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_item_list, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_items)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_dialog_empty)

        container.removeAllViews()

        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            for ((label, value) in items) {
                val rowView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dialog_row, container, false)
                val tvLabel = rowView.findViewById<TextView>(R.id.tv_row_label)
                val tvValue = rowView.findViewById<TextView>(R.id.tv_row_value)

                tvLabel.text = label
                if (value != null) {
                    tvValue.text = "×$value"
                    tvValue.visibility = View.VISIBLE
                } else {
                    tvValue.visibility = View.GONE
                }

                container.addView(rowView)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // ---------- Читалка ----------

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

        binding.tvStoryText.text = scene.text

        if (!appliedSceneEffects.contains(scene.id)) {
            applyEffects(scene.setFlags, scene.removeFlags, scene.modifyStats)
            appliedSceneEffects.add(scene.id)
            Logger.log(requireContext(), "Глава ${currentChapterIndex + 1}, сцена '${scene.id}' загружена впервые")
        }

        updateHud()

        val choices = scene.choices.orEmpty()
        if (choices.isEmpty()) {
            if (currentChapterIndex < chapters.size - 1) {
                val nextChapter = chapters[currentChapterIndex + 1]
                if (nextChapter.requiredSteps > 0) {
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
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка при продвижении главы: ${e.message}")
        }
    }

    private fun applyEffects(setFlags: List<String>?, removeFlags: List<String>?, modifyStats: Map<String, Int>?) {
        if (setFlags != null) {
            for (f in setFlags) {
                if (getCategory(f) == TagCategory.ITEM) {
                    // ITEM как флаг — количество 1
                    val oldValue = stats[f] ?: 0
                    val newValue = oldValue + 1
                    stats[f] = newValue
                    registerItem(f, newValue)
                } else {
                    addFlagIfMissing(f)
                }
            }
        }

        if (removeFlags != null) {
            for (f in removeFlags) {
                if (getCategory(f) == TagCategory.ITEM) {
                    val oldValue = stats[f] ?: 0
                    val newValue = (oldValue - 1).coerceAtLeast(0)
                    stats[f] = newValue
                    registerItem(f, newValue)
                } else {
                    flags.remove(f)
                }
            }
        }

        if (modifyStats != null) {
            modifyStats.forEach { (key, value) ->
                val oldValue = stats[key] ?: 0
                val newValue = oldValue + value
                stats[key] = newValue
                if (getCategory(key) == TagCategory.ITEM) {
                    registerItem(key, newValue)
                }
            }
        }
    }

    private fun updateChoices(choices: List<Choice>) {
        val container = binding.choicesContainer
        container.removeAllViews()

        for (choice in choices) {
            val conditionMet = isConditionMet(choice.condition)

            if (!previewMode && !conditionMet) {
                continue
            }

            val button = Button(requireContext()).apply {
                if (previewMode && !conditionMet) {
                    val conditionLabel = choice.condition ?: ""
                    text = "${choice.text}  🔒 ($conditionLabel)"
                    isEnabled = false
                    setTextColor(Color.parseColor("#808080"))
                    alpha = 0.6f
                } else {
                    text = choice.text
                    setOnClickListener {
                        applyEffects(choice.setFlags, choice.removeFlags, choice.modifyStats)
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
                    flags.contains(flag) || (stats[flag] ?: 0) > 0
                }
                condition.startsWith("!has_") -> {
                    val flag = condition.removePrefix("!has_").trim()
                    !flags.contains(flag) && (stats[flag] ?: 0) <= 0
                }
                condition.contains(">=") -> {
                    val parts = condition.split(">=").map { it.trim() }
                    (stats[parts[0]] ?: 0) >= parts[1].toInt()
                }
                condition.contains("<") -> {
                    val parts = condition.split("<").map { it.trim() }
                    (stats[parts[0]] ?: 0) < parts[1].toInt()
                }
                else -> true
            }
        } catch (e: Exception) {
            true
        }
    }

    // ---------- Шаговые шлюзы ----------

    private fun startChapterSteps(nextChapter: com.myapp.adventure.data.Chapter) {
        val absolute = StepCounterService.absoluteSteps
        goalAbsoluteSteps = absolute + nextChapter.requiredSteps
        startAbsoluteSteps = absolute
        currentStepsDone = 0
        binding.tvStepsRequired.text = "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        saveProgress()
    }

    private fun startChapterStepsInPreview(nextChapter: com.myapp.adventure.data.Chapter) {
        isWaitingForSteps = true
        goalAbsoluteSteps = -1
        startAbsoluteSteps = 0
        currentStepsDone = 0
        showStepsGate(nextChapter)
    }

    private fun showStepsGate(nextChapter: com.myapp.adventure.data.Chapter) {
        binding.tvStepsRequired.text =
            "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        binding.choicesContainer.removeAllViews()
    }

    private fun updateStepsProgress() {
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        binding.tvStepsRequired.text = "Чтобы открыть главу «${nextChapter.title}», пройдите ${nextChapter.requiredSteps} шагов (пройдено $currentStepsDone)."
        binding.tvStepsRequired.visibility = View.VISIBLE
        if (currentStepsDone >= nextChapter.requiredSteps) {
            binding.tvStepsRequired.text = "Цель достигнута! Глава откроется при следующем входе."
        }
    }

    private fun setDebugSteps(value: Int) {
        currentStepsDone = value.coerceAtLeast(0)
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        val required = nextChapter.requiredSteps
        if (required == 0) return

        if (currentStepsDone >= required) {
            onStepsCompleted(nextChapter)
        } else {
            showStepsGate(nextChapter)
        }
    }

    private fun onStepsCompleted(nextChapter: com.myapp.adventure.data.Chapter?) {
        if (nextChapter == null) return
        isWaitingForSteps = false
        binding.tvStepsRequired.visibility = View.GONE
        advanceToNextChapter(nextChapter)
    }

    // ---------- Конец ----------

    private fun showEnd(message: String) {
        binding.tvStoryText.text = if (previewMode) "▶ Конец предпросмотра\n\n$message" else "Конец\n\n$message"
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
        }
        flags.clear()
        stats.clear()
        itemOrder.clear()
        appliedSceneEffects.clear()
        val initialState = adventure.initialState
        initialState?.flags?.forEach { addFlagIfMissing(it) }
        if (initialState?.stats != null) stats.putAll(initialState.stats)
        rebuildItemOrder()

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
            flags = flags.toList(),
            stats = stats.toMap(),
            currentStepsDone = currentStepsDone,
            appliedSceneEffects = appliedSceneEffects.toSet(),
            goalAbsoluteSteps = goalAbsoluteSteps,
            startAbsoluteSteps = startAbsoluteSteps,
            itemOrder = itemOrder.toList()
        )
        ProgressManager.saveProgress(requireContext(), adventureFileUri!!, progress)
    }

    private fun checkPermissions() {
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!notificationPermissionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на уведомления.", Toast.LENGTH_LONG).show()
        }
        if (!activityRecognitionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на физическую активность.", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Отладка ----------

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

        val nextChapterInitial = adventure.chapters?.getOrNull(currentChapterIndex + 1)
        if (isWaitingForSteps && nextChapterInitial != null && nextChapterInitial.requiredSteps > 0) {
            etStepsValue.setText(currentStepsDone.toString())
        }

        fun refreshStepsSection() {
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
            if (isWaitingForSteps && nextChapter != null && nextChapter.requiredSteps > 0) {
                sectionSteps.visibility = View.VISIBLE
                tvStepsStatus.text = "Глава «${nextChapter.title}»: пройдено $currentStepsDone из ${nextChapter.requiredSteps}"
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
                            val oldValue = stats[name] ?: 0
                            stats[name] = newValue
                            if (getCategory(name) == TagCategory.ITEM) {
                                registerItem(name, newValue)
                            }
                            loadCurrentScene()
                            refresh()
                        }
                    }
                    btnDelete.setOnClickListener {
                        stats.remove(name)
                        itemOrder.remove(name)
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
                for (flag in flags.sorted()) {
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
                if (getCategory(name) == TagCategory.ITEM) {
                    registerItem(name, value)
                }
                loadCurrentScene()
                refresh()
            }
        }

        btnAddFlag.setOnClickListener {
            showAddFlagDialog { name ->
                addFlagIfMissing(name)
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
                    itemOrder.clear()
                    val initialState = adventure.initialState
                    initialState?.flags?.forEach { addFlagIfMissing(it) }
                    if (initialState?.stats != null) stats.putAll(initialState.stats)
                    rebuildItemOrder()
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

    // ---------- Меню читалки ----------

    private fun showReaderMenuDialog() {
        val options = arrayOf(
            "В главное меню",
            "Начать приключение заново",
            "Настройки"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Меню")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, MenuFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    1 -> confirmRestartAdventure()
                    2 -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, SettingsFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmRestartAdventure() {
        AlertDialog.Builder(requireContext())
            .setTitle("Начать заново?")
            .setMessage("Текущий прогресс по этому приключению будет удалён. Это действие нельзя отменить.")
            .setPositiveButton("Начать заново") { _, _ -> onRestartAdventure() }
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