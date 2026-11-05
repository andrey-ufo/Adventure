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
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import com.myapp.adventure.databinding.FragmentSettingsBinding
import com.myapp.adventure.step.HealthConnectStepSource
import com.myapp.adventure.step.StepSourceManager
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.launch

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

    // Health Connect permission launcher
    private val hcPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        val required = HealthPermission.getReadPermission(StepsRecord::class)
        val ok = granted.contains(required)
        if (ok) {
            StepSourceManager.setHealthConnectEnabled(requireContext(), true)
            Toast.makeText(requireContext(), "Health Connect включён", Toast.LENGTH_SHORT).show()
        } else {
            StepSourceManager.setHealthConnectEnabled(requireContext(), false)
            binding.cbHealthConnect.isChecked = false
            Toast.makeText(requireContext(), "Разрешение не выдано — используется датчик телефона", Toast.LENGTH_LONG).show()
        }
        refreshHealthConnectStatus()
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

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        // Logging checkbox
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

        setupFontSizeSpinner()
        refreshFontList()

        binding.btnAddFont.setOnClickListener {
            addFontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/vnd.ms-opentype"))
        }

        // ----- Health Connect -----
        setupHealthConnect()
    }

    private fun setupHealthConnect() {
        val ctx = requireContext()
        val hcAvailable = HealthConnectClient.getSdkStatus(ctx, HealthConnectStepSource.PROVIDER_PACKAGE) ==
                HealthConnectClient.SDK_AVAILABLE

        binding.cbHealthConnect.isEnabled = hcAvailable
        binding.cbHealthConnect.isChecked = StepSourceManager.isHealthConnectEnabled(ctx)

        if (!hcAvailable) {
            binding.tvHealthConnectStatus.text = "Health Connect недоступен на этом устройстве"
            binding.tvHealthConnectStatus.visibility = View.VISIBLE
        } else {
            refreshHealthConnectStatus()
        }

        binding.cbHealthConnect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Request permission if not granted
                viewLifecycleOwner.lifecycleScope.launch {
                    val client = HealthConnectClient.getOrCreate(ctx)
                    val granted = client.permissionController.getGrantedPermissions()
                    val required = HealthPermission.getReadPermission(StepsRecord::class)
                    if (granted.contains(required)) {
                        StepSourceManager.setHealthConnectEnabled(ctx, true)
                        refreshHealthConnectStatus()
                    } else {
                        hcPermissionLauncher.launch(setOf(required))
                    }
                }
            } else {
                StepSourceManager.setHealthConnectEnabled(ctx, false)
                refreshHealthConnectStatus()
            }
        }
    }

    private fun refreshHealthConnectStatus() {
        if (_binding == null) return
        val ctx = requireContext()
        val available = HealthConnectClient.getSdkStatus(ctx, HealthConnectStepSource.PROVIDER_PACKAGE) ==
                HealthConnectClient.SDK_AVAILABLE
        if (!available) return

        val enabled = StepSourceManager.isHealthConnectEnabled(ctx)
        val active = StepSourceManager.isHealthConnectActive(ctx)

        binding.tvHealthConnectStatus.visibility = View.VISIBLE
        binding.tvHealthConnectStatus.text = when {
            !enabled -> "Выключено — используется датчик телефона (шаги в реальном времени)"
            active -> "Включено — шаги обновляются с задержкой (Health Connect)"
            else -> "Health Connect недоступен — используется датчик телефона"
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission may have been revoked in system settings while the app was backgrounded.
        // The activity is recreated in that case, but refresh anyway for safety.
        refreshHealthConnectStatus()
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

        binding.spinnerFontSize.onItemSelectedListener = null
        binding.spinnerFontSize.setSelection(currentScaleIdx)

        binding.spinnerFontSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fonts)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFont.adapter = adapter

        val selectedFont = FontManager.getSelectedFont(requireContext())
        val position = fonts.indexOf(selectedFont)
        if (position >= 0) binding.spinnerFont.setSelection(position)

        binding.spinnerFont.onItemSelectedListener = null
        binding.spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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