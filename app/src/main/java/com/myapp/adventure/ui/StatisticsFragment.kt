package com.myapp.adventure.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentStatisticsBinding
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.StepHistoryManager

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем шрифт
        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        binding.btnMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        }

        val weeks = StepHistoryManager.getWeeksDetailed(requireContext())
        val container = binding.containerStats
        container.removeAllViews()

        for (week in weeks) {
            container.addView(TextView(requireContext()).apply {
                text = "${week.label}: ${week.totalSteps} шагов"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8; topMargin = if (container.childCount > 0) 16 else 0 }
            })

            for (day in week.days) {
                container.addView(TextView(requireContext()).apply {
                    text = "${day.dayOfWeek}: ${day.steps} шагов"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 2; leftMargin = 16 }
                })
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}