package com.nehonar.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nehonar.operator.core.design.components.ScanlinesOverlay
import com.nehonar.operator.core.design.theme.OperatorColors
import com.nehonar.operator.core.design.theme.OperatorTheme
import com.nehonar.operator.navigation.OperatorNavHost

@Composable
fun OperatorApp(
    startInListening: Boolean = false,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val scanlinesEnabled by viewModel.scanlinesEnabled.collectAsStateWithLifecycle()
    OperatorTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(OperatorColors.Background),
        ) {
            OperatorNavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                startInListening = startInListening,
            )
            if (scanlinesEnabled) {
                ScanlinesOverlay()
            }
        }
    }
}
