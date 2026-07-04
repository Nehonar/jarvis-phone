package com.nehonar.operator.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.common.formatOperatorDate
import com.nehonar.operator.core.domain.model.MemoryFact
import com.nehonar.operator.core.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryItem(
    val id: String,
    val topic: String,
    val fact: String,
    val dateLabel: String,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    val items: StateFlow<List<MemoryItem>> = memoryRepository.observeAll()
        .map { list -> list.map { it.toItem() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun delete(id: String) {
        viewModelScope.launch {
            memoryRepository.delete(id)
        }
    }
}

private fun MemoryFact.toItem(): MemoryItem = MemoryItem(
    id = id,
    topic = topic,
    fact = fact,
    dateLabel = formatOperatorDate(createdAt.atZone(ZoneId.systemDefault()).toLocalDate()),
)
