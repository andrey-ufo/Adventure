package com.myapp.adventure.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.databinding.FragmentAdventureMapBinding

class AdventureMapFragment : Fragment() {

    private var _binding: FragmentAdventureMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var adapter: AdventureMapAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val topInset = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars()
            ).top
            v.setPadding(0, topInset, 0, 0)
            insets
        }

        binding.tvMapTitle.text = viewModel.title.ifBlank { "Карта приключения" }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnExpandAll.setOnClickListener {
            if (viewModel.scenes.isEmpty()) return@setOnClickListener
            val totalChapters = buildChapterGroups(viewModel.scenes).size
            if (viewModel.isMapChapterExpanded(0) && totalChapters == 1) {
                viewModel.collapseAllMapChapters()
            } else {
                viewModel.collapseAllMapChapters()
                for (i in 0 until totalChapters) {
                    viewModel.toggleMapChapterExpanded(i)
                }
            }
            refresh()
        }

        val items = buildItems()

        adapter = AdventureMapAdapter(
            items = items,
            onChapterClick = { index ->
                viewModel.toggleMapChapterExpanded(index)
                refresh()
            },
            onSceneClick = { uid ->
                viewModel.pendingJumpToSceneUid = uid
                parentFragmentManager.popBackStack()
            }
        )

        binding.rvMap.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMap.adapter = adapter
        binding.rvMap.setItemViewCacheSize(20)

        refresh()
    }

    private fun refresh() {
        val items = buildItems()
        val newAdapter = AdventureMapAdapter(
            items = items,
            onChapterClick = { index ->
                viewModel.toggleMapChapterExpanded(index)
                refresh()
            },
            onSceneClick = { uid ->
                viewModel.pendingJumpToSceneUid = uid
                parentFragmentManager.popBackStack()
            }
        )
        binding.rvMap.adapter = newAdapter
        newAdapter.notifyDataSetChanged()
    }

    private fun buildItems(): List<MapItem> {
        val scenes = viewModel.scenes
        if (scenes.isEmpty()) return emptyList()

        val chapterGroups = buildChapterGroups(scenes)
        viewModel.ensureFirstMapChapterExpanded(chapterGroups.size)

        val items = mutableListOf<MapItem>()

        for ((chapterIdx, group) in chapterGroups.withIndex()) {
            val expanded = viewModel.isMapChapterExpanded(chapterIdx)
            val wordCount = group.sumOf { countWords(it.text) }

            items.add(
                MapItem.ChapterHeader(
                    index = chapterIdx,
                    title = "Глава ${chapterIdx + 1}",
                    sceneCount = group.size,
                    wordCount = wordCount,
                    expanded = expanded
                )
            )

            if (!expanded) {
                // Свёрнутая глава — показываем шлюз (если он есть) и идём дальше
                if (chapterIdx < chapterGroups.size - 1) {
                    val steps = group.lastOrNull()?.requiredSteps ?: 0
                    if (steps >= 0) {
                        items.add(MapItem.StepsGate(steps))
                    }
                }
                continue
            }

            // Развёрнутая глава — сцены с переходами
            val uidById = scenes.associateBy { it.id }.mapValues { it.value.uid }
            for (scene in group) {
                val transitions = scene.choices.map { choice ->
                    val targetUid = uidById[choice.nextSceneId]
                    TransitionInfo(
                        targetSceneUid = targetUid,
                        targetTitle = if (targetUid != null) {
                            scenes.find { it.uid == targetUid }?.title?.ifBlank { "(без названия)" }
                                ?: choice.nextSceneId
                        } else {
                            choice.nextSceneId.ifBlank { "(не задан)" }
                        },
                        condition = choice.conditionTag.ifEmpty { null },
                        missing = targetUid == null && choice.nextSceneId.isNotEmpty()
                    )
                }
                items.add(MapItem.SceneRow(scene = scene, transitions = transitions))
            }

            // Шлюз после главы (если не последняя)
            if (chapterIdx < chapterGroups.size - 1) {
                val steps = group.lastOrNull()?.requiredSteps ?: 0
                if (steps >= 0) {
                    items.add(MapItem.StepsGate(steps))
                }
            }
        }

        return items
    }

    /** Тот же алгоритм группировки, что в buildAdventure() и StoryValidator. */
    private fun buildChapterGroups(scenes: List<EditorScene>): List<List<EditorScene>> {
        val groups = mutableListOf<List<EditorScene>>()
        var current = mutableListOf<EditorScene>()
        for (scene in scenes) {
            current.add(scene)
            if (scene.isChapterEnd || scene == scenes.last()) {
                groups.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}