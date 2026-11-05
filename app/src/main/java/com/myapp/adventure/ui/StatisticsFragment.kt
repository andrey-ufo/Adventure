package com.myapp.adventure.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.myapp.adventure.R
import com.myapp.adventure.data.StepBucket
import com.myapp.adventure.databinding.FragmentStatisticsBinding
import com.myapp.adventure.databinding.ItemStatsDayBinding
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.StepHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    /** First rank threshold (visual only; actual reward logic is future). */
    private val firstRankThreshold: Long = 100_000L

    /** Cached number formatter, recreated if locale changes between onViewCreated calls. */
    private var numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

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

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

        binding.btnMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        }

        loadStats()
    }

    // ==================== Loading ====================

    private fun loadStats() {
        if (_binding == null) return
        binding.tvLoading.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val ctx = requireContext().applicationContext
                StatisticsData(
                    total = StepHistoryManager.getTotalSteps(ctx),
                    today = StepHistoryManager.getTodaySteps(ctx),
                    last7 = StepHistoryManager.getLastNDaysSum(ctx, 7),
                    last30 = StepHistoryManager.getLastNDaysSum(ctx, 30),
                    days = StepHistoryManager.getDailyBuckets(ctx, 14)
                )
            }
            if (_binding == null) return@launch
            binding.tvLoading.visibility = View.GONE
            render(data)
        }
    }

    private data class StatisticsData(
        val total: Long,
        val today: Int,
        val last7: Int,
        val last30: Int,
        val days: List<StepBucket>
    )

    // ==================== Render ====================

    private fun render(data: StatisticsData) {
        if (_binding == null) return

        val hasPermission = hasActivityRecognitionPermission()

        // 1. No permission warning
        binding.tvNoPermission.visibility = if (!hasPermission) View.VISIBLE else View.GONE

        // 2. No data placeholder
        val hasData = data.total > 0L || data.days.any { it.steps > 0 }
        binding.tvNoData.visibility = if (!hasData) View.VISIBLE else View.GONE

        // 3. Summary / recent / days sections only when there's data
        val sectionsVisibility = if (hasData) View.VISIBLE else View.GONE
        binding.sectionSummary.visibility = sectionsVisibility
        binding.sectionRecent.visibility = sectionsVisibility
        binding.sectionDays.visibility = sectionsVisibility

        if (!hasData) return

        // Total
        binding.tvTotalSteps.text = formatSteps(data.total)

        // Rank progress (visual only for now)
        renderRankProgress(data.total)

        // Today / 7 / 30
        binding.tvTodaySteps.text =
            "${getString(R.string.stats_today)}: ${formatSteps(data.today.toLong())}"
        binding.tvLast7DaysSteps.text =
            "${getString(R.string.stats_last_7_days)}: ${formatSteps(data.last7.toLong())}"
        binding.tvLast30DaysSteps.text =
            "${getString(R.string.stats_last_30_days)}: ${formatSteps(data.last30.toLong())}"

        // 14-day list
        renderDays(data.days)
    }

    private fun renderRankProgress(total: Long) {
        val threshold = firstRankThreshold
        val progress: Int
        val progressText: String
        if (total >= threshold) {
            progress = 100
            progressText = getString(
                R.string.stats_rank_achieved,
                getString(R.string.stats_rank_beginner)
            )
            binding.tvRankProgress.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            progress = ((total * 100L) / threshold).toInt().coerceIn(0, 100)
            progressText = getString(
                R.string.stats_rank_progress,
                progress,
                getString(R.string.stats_rank_beginner)
            )
            binding.tvRankProgress.setTextColor(Color.parseColor("#FFC107"))
        }
        binding.progressRank.progress = progress
        binding.tvRankProgress.text = progressText
    }

    private fun renderDays(days: List<StepBucket>) {
        binding.containerDays.removeAllViews()

        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
        val locale = Locale.getDefault()

        // Oldest at the top, newest at the bottom (matches reading order of a timeline).
        for (bucket in days) {
            val date = try {
                LocalDate.parse(bucket.date)
            } catch (_: Exception) {
                continue
            }

            val row = ItemStatsDayBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.containerDays,
                false
            )

            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val isToday = date == today
            val dateLabel = if (isToday) {
                "${dateFormatter.format(date)} · ${getString(R.string.stats_today).lowercase(locale)}"
            } else {
                "$dayOfWeek, ${dateFormatter.format(date)}"
            }

            row.tvDayLabel.text = dateLabel
            row.tvDaySteps.text = formatSteps(bucket.steps.toLong())
            // Dim days with zero steps so they don't dominate the screen.
            row.tvDaySteps.setTextColor(
                if (bucket.steps == 0) Color.parseColor("#666666")
                else Color.parseColor("#FFC107")
            )

            binding.containerDays.addView(row.root)
        }
    }

    // ==================== Helpers ====================

    private fun formatSteps(value: Long): String {
        val formattedNumber = numberFormat.format(value)
        // Use plurals with the raw count so Android picks the right form.
        val stepsText = resources.getQuantityString(
            R.plurals.steps,
            value.toInt(),
            value.toInt()
        )
        // We only want the plural form word ("шаг"/"шага"/"шагов"), not the formatted number.
        // The plural string starts with "%d" — strip it.
        val suffix = stepsText.replace(value.toString(), "").trim()
        return "$formattedNumber $suffix"
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}