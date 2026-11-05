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
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.PlayerStateSnapshot
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
import kotlinx.coroutines.launch

class AdventureReaderFragment : Fragment() {

    private var _binding: FragmentAdventureReadBinding? = null
    private val binding get() = _binding!!

    private lateinit var adventure: Adventure
    private var adventureFileUri: Uri? = null

    private var currentChapterIndex = 0
    private var currentSceneId = ""

    // ----- Steps gate state -----

    /** Steps remaining until the next chapter opens. */
    private var remainingSteps = 0

    /** Whether a steps gate is currently active. */
    private var isGateActive = false

    /**
     * Raw sensor reading at the moment this fragment last synchronized with the sensor.
     * -1 means "not yet known" — we wait for the first broadcast or fall back to
     * [lastAbsoluteAtSaveFromProgress] for catch-up.
     */
    private var lastKnownAbsolute = -1

    /**
     * lastAbsoluteAtSave restored from ProgressData at load.
     * Used once to catch up steps taken while the app was closed.
     */
    private var lastAbsoluteAtSaveFromProgress = -1

    private var notificationPermissionGranted = false
    private var activityRecognitionGranted = false

    private var previewMode = false
    private var previewStartSceneId = ""
    private var previewStartChapterIndex = 0

    private var isDead = false

    private var previousStepSnapshot: PlayerStateSnapshot? = null
    private var chapterStartSnapshot: PlayerStateSnapshot? = null

    private val flags = mutableListOf<String>()
    private val stats = mutableMapOf<String, Int>()
    private val itemOrder = mutableListOf<String>()
    private val appliedSceneEffects = mutableSetOf<String>()

    private val tagRegistry: Map<String, TagDefinition>
        get() = adventure.tags.orEmpty().associateBy { it.name }

    private val stepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isGateActive) return
            val absoluteSteps = intent?.getIntExtra(StepCounterConstants.EXTRA_ABSOLUTE_STEPS, 0) ?: 0
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
            if (nextChapter.requiredSteps <= 0) return

            if (lastKnownAbsolute < 0) {
                // First event since fragment opened. Catch up from saved baseline.
                if (lastAbsoluteAtSaveFromProgress >= 0) {
                    val delta = (absoluteSteps - lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
                    remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
                }
                lastKnownAbsolute = absoluteSteps
            } else {
                val delta = (absoluteSteps - lastKnownAbsolute).coerceAtLeast(0)
                lastKnownAbsolute = absoluteSteps
                if (delta > 0) {
                    remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
                }
            }

            if (remainingSteps <= 0) {
                onStepsCompleted(nextChapter)
            } else {
                updateStepsProgressUi(nextChapter)
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
            Logger.log(requireContext(), "Error loading adventure: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    // ==================== State init ====================

    private fun addFlagIfMissing(flag: String) {
        if (!flags.contains(flag)) flags.add(flag)
    }

    private fun getCategory(name: String): TagCategory {
        if (name == "health") return TagCategory.HEALTH
        return tagRegistry[name]?.category ?: TagCategory.JOURNAL
    }

    private fun getDisplayName(name: String): String {
        val def = tagRegistry[name]
        return def?.displayName?.ifBlank { name } ?: name
    }

    private fun isVisible(name: String): Boolean {
        return tagRegistry[name]?.visible ?: false
    }

    private fun registerItem(name: String, newCount: Int) {
        if (getCategory(name) != TagCategory.ITEM) return
        if (newCount > 0 && !itemOrder.contains(name)) {
            itemOrder.add(name)
        } else if (newCount <= 0) {
            itemOrder.remove(name)
        }
    }

    private fun rebuildItemOrder() {
        itemOrder.clear()
        adventure.tags?.filter { it.category == TagCategory.ITEM }?.forEach { def ->
            if ((stats[def.name] ?: 0) > 0) {
                itemOrder.add(def.name)
            }
        }
    }

    private fun captureSnapshot(): PlayerStateSnapshot = PlayerStateSnapshot(
        sceneId = currentSceneId,
        chapterIndex = currentChapterIndex,
        flags = flags.toList(),
        stats = stats.toMap(),
        itemOrder = itemOrder.toList(),
        appliedSceneEffects = appliedSceneEffects.toSet()
    )

    private fun restoreSnapshot(snapshot: PlayerStateSnapshot) {
        currentChapterIndex = snapshot.chapterIndex
        currentSceneId = snapshot.sceneId
        flags.clear()
        flags.addAll(snapshot.flags)
        stats.clear()
        stats.putAll(snapshot.stats)
        itemOrder.clear()
        itemOrder.addAll(snapshot.itemOrder)
        appliedSceneEffects.clear()
        appliedSceneEffects.addAll(snapshot.appliedSceneEffects)
        isDead = false
        saveProgress()
        loadCurrentScene()
    }

    private fun captureChapterStartIfNeeded() {
        val chapter = adventure.chapters?.getOrNull(currentChapterIndex) ?: return
        val firstSceneId = chapter.scenes?.firstOrNull()?.id ?: return
        if (currentSceneId != firstSceneId) return
        if (chapterStartSnapshot != null && chapterStartSnapshot!!.chapterIndex == currentChapterIndex) return
        val health = stats["health"]
        if (health == null || health > 0) {
            chapterStartSnapshot = captureSnapshot()
        }
    }

    private fun checkDeath(): Boolean {
        if (isDead) return false
        val health = stats["health"] ?: return false
        if (health > 0) return false
        isDead = true
        return true
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
        resetGate()
        isDead = false
        previousStepSnapshot = null
        chapterStartSnapshot = null

        Logger.log(
            requireContext(),
            "Preview: chapter ${previewStartChapterIndex + 1}, scene '$previewStartSceneId'"
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
            remainingSteps = saved.remainingSteps
            isGateActive = saved.isGateActive
            lastAbsoluteAtSaveFromProgress = saved.lastAbsoluteAtSave
            lastKnownAbsolute = -1

            flags.clear()
            saved.flags.forEach { addFlagIfMissing(it) }
            stats.clear()
            stats.putAll(saved.stats)
            appliedSceneEffects.clear()
            appliedSceneEffects.addAll(saved.appliedSceneEffects)
            itemOrder.clear()
            if (saved.itemOrder.isNotEmpty()) {
                for (name in saved.itemOrder) {
                    if ((stats[name] ?: 0) > 0) itemOrder.add(name)
                }
            } else {
                rebuildItemOrder()
            }
            previousStepSnapshot = saved.previousStepSnapshot
            chapterStartSnapshot = saved.chapterStartSnapshot
            isDead = false
            Logger.log(
                requireContext(),
                "Progress loaded: chapter ${currentChapterIndex + 1}, scene '$currentSceneId', " +
                        "gate=$isGateActive, remaining=$remainingSteps, lastAbsAtSave=$lastAbsoluteAtSaveFromProgress"
            )
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
            resetGate()
            previousStepSnapshot = null
            chapterStartSnapshot = null
            isDead = false
            Logger.log(
                requireContext(),
                "Adventure '${adventure.title}' loaded, chapter ${currentChapterIndex + 1}"
            )
        }

        tryCatchUpGate()
    }

    // ==================== Lifecycle ====================

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
            if (!previewMode) checkPermissions()

            if (previewMode) {
                binding.btnMenu.text = "Назад"
                binding.btnMenu.setOnClickListener { parentFragmentManager.popBackStack() }
                binding.btnDebug.visibility = View.VISIBLE
                binding.btnDebug.setOnClickListener { showDebugPanel() }
            } else {
                binding.btnMenu.setOnClickListener { showReaderMenuDialog() }
                binding.btnDebug.visibility = View.GONE
            }

            binding.btnHealth.setOnClickListener { showHealthDialog() }
            binding.btnInventory.setOnClickListener { showInventoryDialog() }
            binding.btnJournal.setOnClickListener { showJournalDialog() }

            binding.btnNextChapter.visibility = View.GONE

            // If the sensor was already alive when the fragment opened, catch up
            // immediately so the player sees the correct remaining count without
            // waiting for the next broadcast.
            tryCatchUpGate()

            loadCurrentScene()
            updateHud()

            if (!previewMode) {
                ContextCompat.registerReceiver(
                    requireContext(),
                    stepReceiver,
                    IntentFilter(StepCounterConstants.ACTION_UPDATE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Error in onViewCreated: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка запуска читалки: ${e.message}", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onStop() {
        super.onStop()
        if (!previewMode && adventureFileUri != null) {
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                ProgressManager.flush(appContext)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!previewMode) {
            try {
                requireContext().unregisterReceiver(stepReceiver)
            } catch (_: Exception) {
            }
        }
        _binding = null
    }

    private fun updateHud() {
        if (_binding == null) return

        val healthDef = adventure.tags?.firstOrNull { it.name == "health" && it.category == TagCategory.HEALTH }
            ?: adventure.tags?.firstOrNull { it.category == TagCategory.HEALTH }
        val healthValue = healthDef?.let { stats[it.name] ?: 0 } ?: 0
        binding.btnHealth.text = "❤️ $healthValue"

        val itemCount = itemOrder.count { (stats[it] ?: 0) > 0 }
        binding.btnInventory.text = "🎒 $itemCount"

        val journalCount = getJournalItems().size
        binding.btnJournal.text = "📖 $journalCount"
    }

    // ==================== Lists / dialogs (HUD) ====================

    private fun getHealthItems(): List<Pair<String, Int>> {
        return adventure.tags.orEmpty()
            .filter { it.category == TagCategory.HEALTH }
            .map { def ->
                val display = def.displayName.ifBlank { def.name }
                display to (stats[def.name] ?: 0)
            }
    }

    private fun getInventoryItems(): List<Pair<String, Int>> {
        return itemOrder.asReversed()
            .filter { (stats[it] ?: 0) > 0 }
            .map { name -> getDisplayName(name) to (stats[name] ?: 0) }
    }

    private fun getJournalItems(): List<String> {
        val result = mutableListOf<String>()
        for (name in flags.asReversed()) {
            if (isVisible(name) && getCategory(name) == TagCategory.JOURNAL) {
                result.add(getDisplayName(name))
            }
        }
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

    private fun showHealthDialog() {
        showListDialog("❤️ Здоровье", getHealthItems())
    }

    private fun showInventoryDialog() {
        showListDialog("🎒 Рюкзак", getInventoryItems())
    }

    private fun showJournalDialog() {
        showListDialog("📖 Журнал", getJournalItems().map { it to null })
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

    // ==================== Scene loading ====================

    private fun loadCurrentScene() {
        if (isDead) return

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
            Logger.log(
                requireContext(),
                "Chapter ${currentChapterIndex + 1}, scene '${scene.id}' loaded for the first time"
            )
        }

        updateHud()

        if (checkDeath()) {
            saveProgress()
            showDeathScreen()
            return
        }

        captureChapterStartIfNeeded()

        val choices = scene.choices.orEmpty()
        if (choices.isEmpty()) {
            if (currentChapterIndex < chapters.size - 1) {
                val nextChapter = chapters[currentChapterIndex + 1]
                if (nextChapter.requiredSteps > 0) {
                    if (isGateActive) {
                        // Gate was restored from saved progress — keep remainingSteps.
                        showGateWaiting(nextChapter)
                    } else {
                        if (previewMode) startChapterStepsInPreview(nextChapter)
                        else startChapterSteps(nextChapter)
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
            resetGate()
            previousStepSnapshot = null
            saveProgress()
            if (_binding != null) loadCurrentScene()
        } catch (e: Exception) {
            Logger.log(requireContext(), "Error advancing chapter: ${e.message}")
        }
    }

    private fun applyEffects(
        setFlags: List<String>?,
        removeFlags: List<String>?,
        modifyStats: Map<String, Int>?
    ) {
        if (setFlags != null) {
            for (f in setFlags) {
                val cat = getCategory(f)
                if (cat == TagCategory.JOURNAL) {
                    addFlagIfMissing(f)
                } else {
                    Logger.log(requireContext(), "applyEffects: set_flags '$f' (category $cat) ignored")
                }
            }
        }

        if (removeFlags != null) {
            for (f in removeFlags) {
                val cat = getCategory(f)
                if (cat == TagCategory.JOURNAL) {
                    flags.remove(f)
                } else {
                    Logger.log(requireContext(), "applyEffects: remove_flags '$f' (category $cat) ignored")
                }
            }
        }

        if (modifyStats != null) {
            modifyStats.forEach { (key, value) ->
                val oldValue = stats[key] ?: 0
                val rawNew = oldValue + value
                val cat = getCategory(key)
                val newValue = when (cat) {
                    TagCategory.HEALTH, TagCategory.ITEM -> rawNew.coerceAtLeast(0)
                    TagCategory.JOURNAL -> rawNew
                }
                stats[key] = newValue
                if (cat == TagCategory.ITEM) registerItem(key, newValue)
            }
        }
    }

    private fun updateChoices(choices: List<Choice>) {
        val container = binding.choicesContainer
        container.removeAllViews()

        for (choice in choices) {
            val conditionMet = isConditionMet(choice.condition)
            if (!previewMode && !conditionMet) continue

            val button = Button(requireContext()).apply {
                if (previewMode && !conditionMet) {
                    text = "${choice.text}  🔒 (${choice.condition ?: ""})"
                    isEnabled = false
                    setTextColor(Color.parseColor("#808080"))
                    alpha = 0.6f
                } else {
                    text = choice.text
                    setOnClickListener {
                        previousStepSnapshot = captureSnapshot()
                        applyEffects(choice.setFlags, choice.removeFlags, choice.modifyStats)
                        currentSceneId = choice.nextScene
                        saveProgress()

                        if (checkDeath()) {
                            showDeathScreen()
                            return@setOnClickListener
                        }

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

    // ==================== Death screen ====================

    private fun showDeathScreen() {
        binding.tvStepsRequired.visibility = View.GONE
        binding.choicesContainer.removeAllViews()

        val deathText = adventure.deathScene?.text ?: "💀 Вы погибли."
        binding.tvStoryText.text = deathText

        val allowStepBack = adventure.deathScene?.allowStepBack ?: true
        val allowChapter = adventure.deathScene?.allowChapterRestart ?: true
        val allowAdventure = adventure.deathScene?.allowAdventureRestart ?: true

        if (allowStepBack && previousStepSnapshot != null) {
            val btn = Button(requireContext()).apply {
                text = "↩ Один шаг назад"
                setOnClickListener { onStepBack() }
            }
            binding.choicesContainer.addView(btn)
        }

        if (allowChapter && chapterStartSnapshot != null && chapterStartSnapshot!!.chapterIndex == currentChapterIndex) {
            val btn = Button(requireContext()).apply {
                text = "📖 В начало главы"
                setOnClickListener { onChapterRestart() }
            }
            binding.choicesContainer.addView(btn)
        }

        if (allowAdventure) {
            val btn = Button(requireContext()).apply {
                text = if (previewMode) "🔄 Начать предпросмотр заново" else "🏠 Начать приключение заново"
                setOnClickListener { onAdventureRestart() }
            }
            binding.choicesContainer.addView(btn)
        }
    }

    private fun onStepBack() {
        val snapshot = previousStepSnapshot ?: return
        restoreSnapshot(snapshot)
    }

    private fun onChapterRestart() {
        val snapshot = chapterStartSnapshot ?: return
        previousStepSnapshot = null
        restoreSnapshot(snapshot)
    }

    private fun onAdventureRestart() {
        if (previewMode) {
            flags.clear()
            stats.clear()
            itemOrder.clear()
            appliedSceneEffects.clear()
            val initialState = adventure.initialState
            initialState?.flags?.forEach { addFlagIfMissing(it) }
            if (initialState?.stats != null) stats.putAll(initialState.stats)
            rebuildItemOrder()
            currentChapterIndex = previewStartChapterIndex
            currentSceneId = previewStartSceneId
            resetGate()
            isDead = false
            previousStepSnapshot = null
            chapterStartSnapshot = null
            loadCurrentScene()
        } else {
            isDead = false
            previousStepSnapshot = null
            chapterStartSnapshot = null
            onRestartAdventure()
        }
    }

    // ==================== Steps gate ====================

    private fun resetGate() {
        isGateActive = false
        remainingSteps = 0
        lastKnownAbsolute = -1
        lastAbsoluteAtSaveFromProgress = -1
    }

    /**
     * If a gate is restored from saved progress and the sensor is already alive,
     * subtract the delta between the last save and the current absolute reading.
     * Idempotent: safe to call from onCreate and onViewCreated.
     */
    private fun tryCatchUpGate() {
        if (!isGateActive || previewMode) return
        if (lastKnownAbsolute >= 0) return // already caught up or just started

        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
        if (nextChapter == null || nextChapter.requiredSteps <= 0) {
            resetGate()
            return
        }

        if (StepCounterService.hasFirstEvent && lastAbsoluteAtSaveFromProgress >= 0) {
            val delta = (StepCounterService.absoluteSteps - lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
            remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
            lastKnownAbsolute = StepCounterService.absoluteSteps
            if (remainingSteps <= 0) {
                onStepsCompleted(nextChapter)
            }
        }
        // else: sensor is not awake yet; catch-up will happen on the first broadcast.
    }

    private fun startChapterSteps(nextChapter: com.myapp.adventure.data.Chapter) {
        isGateActive = true
        remainingSteps = nextChapter.requiredSteps
        lastKnownAbsolute = if (StepCounterService.hasFirstEvent) StepCounterService.absoluteSteps else -1
        lastAbsoluteAtSaveFromProgress = -1
        if (_binding != null) showGateWaiting(nextChapter)
        saveProgress()
    }

    private fun startChapterStepsInPreview(nextChapter: com.myapp.adventure.data.Chapter) {
        isGateActive = true
        remainingSteps = nextChapter.requiredSteps
        lastKnownAbsolute = -1
        showGateWaiting(nextChapter)
    }

    private fun showGateWaiting(nextChapter: com.myapp.adventure.data.Chapter) {
        if (_binding == null) return
        binding.choicesContainer.removeAllViews()
        if (lastKnownAbsolute < 0) {
            binding.tvStepsRequired.text = "Ожидание данных шагомера…"
        } else {
            updateStepsProgressUi(nextChapter)
        }
        if (com.myapp.adventure.step.StepSourceManager.isHealthConnectActive(requireContext())) {
            binding.tvStepsRequired.append("\n(Health Connect: обновление раз в ~1 минуту)")
        }
        binding.tvStepsRequired.visibility = View.VISIBLE
    }

    private fun updateStepsProgressUi(nextChapter: com.myapp.adventure.data.Chapter) {
        if (_binding == null) return
        val total = nextChapter.requiredSteps
        val done = (total - remainingSteps).coerceIn(0, total)
        binding.tvStepsRequired.text =
            "Чтобы открыть главу «${nextChapter.title}», пройдено $done из $total шагов (осталось $remainingSteps)."
        binding.tvStepsRequired.visibility = View.VISIBLE
    }

    private fun onStepsCompleted(nextChapter: com.myapp.adventure.data.Chapter?) {
        if (nextChapter == null) return
        advanceToNextChapter(nextChapter)
    }

    private fun setDebugRemainingSteps(remaining: Int) {
        remainingSteps = remaining.coerceAtLeast(0)
        val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1) ?: return
        if (nextChapter.requiredSteps <= 0) return
        if (remainingSteps <= 0) {
            onStepsCompleted(nextChapter)
        } else {
            updateStepsProgressUi(nextChapter)
        }
    }

    // ==================== End / restart ====================

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
                if (previewMode) parentFragmentManager.popBackStack()
                else onRestartAdventure()
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

        currentChapterIndex = 0
        currentSceneId = adventure.chapters?.firstOrNull()?.scenes?.firstOrNull()?.id ?: ""
        resetGate()
        isDead = false
        previousStepSnapshot = null
        chapterStartSnapshot = null
        saveProgress()
        loadCurrentScene()
        Toast.makeText(requireContext(), "Приключение начато заново", Toast.LENGTH_SHORT).show()
    }

    // ==================== Progress persistence ====================

    private fun saveProgress() {
        if (previewMode) return
        if (adventureFileUri == null) return

        val absoluteAtSave = if (StepCounterService.hasFirstEvent) StepCounterService.absoluteSteps else -1
        val progress = ProgressData(
            adventureFileName = "",
            currentChapterIndex = currentChapterIndex,
            currentSceneId = currentSceneId,
            flags = flags.toList(),
            stats = stats.toMap(),
            appliedSceneEffects = appliedSceneEffects.toSet(),
            itemOrder = itemOrder.toList(),
            previousStepSnapshot = previousStepSnapshot,
            chapterStartSnapshot = chapterStartSnapshot,
            remainingSteps = remainingSteps,
            isGateActive = isGateActive,
            lastAbsoluteAtSave = absoluteAtSave
        )
        ProgressManager.saveProgress(requireContext(), adventureFileUri!!, progress)
    }

    // ==================== Permissions ====================

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

    // ==================== Menu ====================

    private fun showReaderMenuDialog() {
        val options = arrayOf("В главное меню", "Начать приключение заново", "Настройки")
        AlertDialog.Builder(requireContext())
            .setTitle("Меню")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MenuFragment())
                        .addToBackStack(null)
                        .commit()
                    1 -> confirmRestartAdventure()
                    2 -> parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SettingsFragment())
                        .addToBackStack(null)
                        .commit()
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

    // ==================== Debug panel (preview) ====================

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
        if (isGateActive && nextChapterInitial != null && nextChapterInitial.requiredSteps > 0) {
            etStepsValue.setText(remainingSteps.toString())
        }

        fun refreshStepsSection() {
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
            if (isGateActive && nextChapter != null && nextChapter.requiredSteps > 0) {
                sectionSteps.visibility = View.VISIBLE
                val total = nextChapter.requiredSteps
                val done = (total - remainingSteps).coerceIn(0, total)
                tvStepsStatus.text =
                    "Глава «${nextChapter.title}»: пройдено $done из $total (осталось $remainingSteps)"
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
                            if (getCategory(name) == TagCategory.ITEM) registerItem(name, newValue)
                            isDead = false
                            loadCurrentScene()
                            refresh()
                        }
                    }
                    btnDelete.setOnClickListener {
                        stats.remove(name)
                        itemOrder.remove(name)
                        isDead = false
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
                if (getCategory(name) == TagCategory.ITEM) registerItem(name, value)
                isDead = false
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
            setDebugRemainingSteps(value)
            refresh()
        }

        btnOpenChapterNow.setOnClickListener {
            val nextChapter = adventure.chapters?.getOrNull(currentChapterIndex + 1)
            if (nextChapter == null || nextChapter.requiredSteps == 0) {
                Toast.makeText(requireContext(), "Шлюз неактивен", Toast.LENGTH_SHORT).show()
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
                    isDead = false
                    loadCurrentScene()
                    refresh()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        refresh()
        dialog.show()
    }

    private fun showEditStatDialog(statName: String, currentValue: Int, onResult: (Int) -> Unit) {
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
}