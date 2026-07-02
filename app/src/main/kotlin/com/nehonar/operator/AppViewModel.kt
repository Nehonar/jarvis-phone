package com.nehonar.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nehonar.operator.core.datastore.OperatorPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: OperatorPreferences,
) : ViewModel() {

    val scanlinesEnabled: StateFlow<Boolean> = preferences.scanlinesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
