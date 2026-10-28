package com.myapp.adventure.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.myapp.adventure.databinding.FragmentLogViewerBinding
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger

class LogViewerFragment : Fragment() {

    private var _binding: FragmentLogViewerBinding? = null
    private val binding get() = _binding!!

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

        // Применяем шрифт
        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        val logText = Logger.getLogContent(requireContext())
        binding.tvLogContent.text = logText

        binding.btnCopyLog.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("log", logText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Лог скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}