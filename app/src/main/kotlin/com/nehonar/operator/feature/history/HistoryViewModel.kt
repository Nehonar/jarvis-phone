package com.nehonar.operator.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.IntentType
import com.nehonar.operator.core.common.formatOperatorDateTime
import com.nehonar.operator.core.domain.model.VoiceNoteStatus
import com.nehonar.operator.core.domain.repository.ParsedIntentRepository
import com.nehonar.operator.core.domain.repository.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItem(
    val id: String,
    val header: String,
    val status: VoiceNoteStatus,
    val transcript: String,
    val intentType: IntentType?,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val voiceNoteRepository: VoiceNoteRepository,
    private val parsedIntentRepository: ParsedIntentRepository,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = combine(
        voiceNoteRepository.observeAll(),
        parsedIntentRepository.observeIntentTypesByVoiceNoteId(),
    ) { notes, intentTypes ->
        notes.map { note ->
            HistoryItem(
                id = note.id,
                header = formatOperatorDateTime(note.createdAt, ZoneId.systemDefault()),
                status = note.status,
                transcript = note.transcript,
                intentType = intentTypes[note.id],
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun delete(id: String) {
        viewModelScope.launch {
            parsedIntentRepository.deleteByVoiceNoteId(id)
            voiceNoteRepository.delete(id)
        }
    }
}
