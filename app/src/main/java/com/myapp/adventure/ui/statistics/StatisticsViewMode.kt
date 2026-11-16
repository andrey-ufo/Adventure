package com.myapp.adventure.ui.statistics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.data.StepBucket
import com.myapp.adventure.utils.StepHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val total: Long = 0L,
    val today: Int = 0,
    val last7: Int = 0,
    val last30: Int = 0,
    val days: List<StepBucket> = emptyList(),
    val hasActivityPermission: Boolean = true,
) {
    val hasData: Boolean
        get() = total > 0L || days.any { it.steps > 0 }
}

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val app = getApplication<Application>()
            val hasPermission = hasActivityRecognitionPermission()

            // StepHistoryManager reads from SAF-backed files → keep off the main thread.
            val data = withContext(Dispatchers.IO) {
                StatisticsSnapshot(
                    total = StepHistoryManager.getTotalSteps(app),
                    today = StepHistoryManager.getTodaySteps(app),
                    last7 = StepHistoryManager.getLastNDaysSum(app, 7),
                    last30 = StepHistoryManager.getLastNDaysSum(app, 30),
                    days = StepHistoryManager.getDailyBuckets(app, 14),
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    total = data.total,
                    today = data.today,
                    last7 = data.last7,
                    last30 = data.last30,
                    days = data.days,
                    hasActivityPermission = hasPermission,
                )
            }
        }
    }

    private data class StatisticsSnapshot(
        val total: Long,
        val today: Int,
        val last7: Int,
        val last30: Int,
        val days: List<StepBucket>,
    )

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }
}