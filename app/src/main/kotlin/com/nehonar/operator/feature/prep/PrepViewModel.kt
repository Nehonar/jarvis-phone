package com.nehonar.operator.feature.prep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.ai.ActionType
import com.nehonar.operator.core.domain.model.ChecklistItem
import com.nehonar.operator.core.domain.repository.ChecklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrepGroup(
    val type: ActionType,
    val items: List<ChecklistItem>,
)

data class PrepUiState(
    val groups: List<PrepGroup> = emptyList(),
    val openCount: Int = 0,
    val doneCount: Int = 0,
)

@HiltViewModel
class PrepViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
) : ViewModel() {

    val uiState: StateFlow<PrepUiState> = checklistRepository.observeAll()
        .map { items ->
            PrepUiState(
                groups = items
                    .groupBy { it.type }
                    .map { (type, grouped) -> PrepGroup(type, grouped) }
                    .sortedBy { it.type.ordinal },
                openCount = items.count { !it.done },
                doneCount = items.count { it.done },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrepUiState(),
        )

    fun toggle(id: String) {
        viewModelScope.launch {
            val item = checklistRepository.getById(id) ?: return@launch
            checklistRepository.save(item.copy(done = !item.done))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            checklistRepository.delete(id)
        }
    }

    fun clearDone() {
        viewModelScope.launch {
            checklistRepository.deleteDone()
        }
    }
}
