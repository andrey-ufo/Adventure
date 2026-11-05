package com.myapp.adventure.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.myapp.adventure.databinding.FragmentLogViewerBinding
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.launch

class LogViewerFragment : Fragment() {

    private var _binding: FragmentLogViewerBinding? = null
    private val binding get() = _binding!!

    /** Cached log text so "Copy" works without an extra disk read. */
    private var currentLogText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        binding.btnCopyLog.setOnClickListener { copyLogToClipboard() }
        binding.btnClearLog.setOnClickListener { confirmClearLog() }

        loadLog()
    }

    private fun loadLog() {
        if (_binding == null) return
        binding.tvLogContent.text = "Загрузка…"

        viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val logText = Logger.getLogContent(appContext)
            if (_binding == null) return@launch
            currentLogText = logText
            binding.tvLogContent.text = logText
        }
    }

    private fun copyLogToClipboard() {
        if (currentLogText.isEmpty()) {
            Toast.makeText(requireContext(), "Лог пуст", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("log", currentLogText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Лог скопирован в буфер обмена", Toast.LENGTH_SHORT)
            .show()
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить лог?")
            .setMessage(
                "Содержимое debug_log.txt будет удалено. " +
                        "Незаписанные строки из буфера тоже будут потеряны. " +
                        "Это действие нельзя отменить."
            )
            .setPositiveButton("Очистить") { _, _ -> clearLog() }
            .setNegativeButton("Отмена", null)
            .show()
    }

                        private fun clearLog() {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val appContext = requireContext().applicationContext
                        Logger.clearLog(appContext)
                        val newLogText = Logger.getLogContent(appContext)
                        if (_binding == null) return@launch
                        currentLogText = newLogText
                        binding.tvLogContent.text = newLogText
                        Toast.makeText(requireContext(), "Лог очищен", Toast.LENGTH_SHORT).show()
                    }
                }

                        override fun onDestroyView() {
                    super.onDestroyView()
                    _binding = null
                }
    }