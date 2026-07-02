package com.nehonar.operator.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.TimeProvider
import com.nehonar.operator.core.common.formatOperatorDate
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val dateLabel: String,
    val noteCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    timeProvider: TimeProvider,
    repository: VoiceNoteRepository,
) : ViewModel() {

    private val dateLabel = formatOperatorDate(timeProvider.today())

    val uiState: StateFlow<HomeUiState> = repository.observeAll()
        .map { notes -> HomeUiState(dateLabel = dateLabel, noteCount = notes.size) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(dateLabel = dateLabel),
        )
}
