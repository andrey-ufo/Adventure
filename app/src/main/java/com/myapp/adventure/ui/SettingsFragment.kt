package com.myapp.adventure.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.myapp.adventure.databinding.FragmentSettingsBinding
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val addFontLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "font.ttf"
                } else "font.ttf"
            } ?: "font.ttf"

            FontManager.addFont(requireContext(), uri, fileName)
            refreshFontList()
            Toast.makeText(requireContext(), "Шрифт добавлен: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем текущий шрифт ко всем элементам этого фрагмента
        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        // Чекбокс логирования
        val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
        val loggingEnabled = prefs.getBoolean("logging_enabled", false)
        binding.cbLogging.isChecked = loggingEnabled

        binding.cbLogging.setOnCheckedChangeListener { _, isChecked ->
            Logger.setEnabled(requireContext(), isChecked)
            Toast.makeText(
                requireContext(),
                if (isChecked) "Логирование включено" else "Логирование выключено",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Выбор размера шрифта
        setupFontSizeSpinner()

        // Выбор шрифта
        refreshFontList()

        binding.btnAddFont.setOnClickListener {
            addFontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/vnd.ms-opentype"))
        }
    }

    private fun setupFontSizeSpinner() {
        val scales = FontManager.FONT_SCALES
        val sizeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            scales.map { it.first }
        )
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFontSize.adapter = sizeAdapter

        val currentScale = FontManager.getFontScale(requireContext())
        val currentScaleIdx = scales.indexOfFirst { it.second == currentScale }.coerceAtLeast(0)

        // Сначала сбрасываем слушатель, чтобы setSelection не дёрнул его
        binding.spinnerFontSize.onItemSelectedListener = null
        binding.spinnerFontSize.setSelection(currentScaleIdx)

        binding.spinnerFontSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val newScale = scales[position].second
                if (newScale != FontManager.getFontScale(requireContext())) {
                    FontManager.setFontScale(requireContext(), newScale)
                    requireActivity().recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshFontList() {
        val fonts = FontManager.getAvailableFonts(requireContext())
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fonts
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFont.adapter = adapter

        val selectedFont = FontManager.getSelectedFont(requireContext())
        val position = fonts.indexOf(selectedFont)
        if (position >= 0) {
            binding.spinnerFont.setSelection(position)
        }

        binding.spinnerFont.onItemSelectedListener = null
        binding.spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val fontName = fonts[position]
                val oldFont = FontManager.getSelectedFont(requireContext())
                if (fontName != oldFont) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Смена шрифта")
                        .setMessage("Применить шрифт \"$fontName\" ко всем экранам? Приложение будет перезапущено.")
                        .setPositiveButton("Да") { _, _ ->
                            FontManager.setSelectedFont(requireContext(), fontName)
                            requireActivity().recreate()
                        }
                        .setNegativeButton("Отмена") { _, _ ->
                            val oldPos = fonts.indexOf(oldFont)
                            if (oldPos >= 0) binding.spinnerFont.setSelection(oldPos)
                        }
                        .show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}