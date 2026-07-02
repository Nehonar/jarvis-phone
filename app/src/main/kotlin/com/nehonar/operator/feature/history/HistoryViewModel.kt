package com.nehonar.operator.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.domain.model.VoiceNote
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItem(
    val id: String,
    val header: String,
    val status: VoiceNoteStatus,
    val transcript: String,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: VoiceNoteRepository,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = repository.observeAll()
        .map { notes -> notes.map { it.toItem() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    private fun VoiceNote.toItem() = HistoryItem(
        id = id,
        header = formatOperatorDateTime(createdAt, ZoneId.systemDefault()),
        status = status,
        transcript = transcript,
    )
}
