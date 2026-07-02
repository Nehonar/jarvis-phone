package com.nehonar.operator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Smoke de navegación: requiere dispositivo/emulador; no se ejecuta en CI (Fase 0).
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeMuestraCabecera() {
        composeRule.onNodeWithText("OPERATOR // DAY STATUS").assertIsDisplayed()
    }

    @Test
    fun navegaAConsolaYVuelve() {
        composeRule.onNodeWithText("[ CONSOLE ]").performClick()
        composeRule.onNodeWithText("OPERATOR // CONSOLE").assertIsDisplayed()

        composeRule.onNodeWithText("[ BACK ]").performClick()
        composeRule.onNodeWithText("OPERATOR // DAY STATUS").assertIsDisplayed()
    }

    @Test
    fun navegaAConfig() {
        composeRule.onNodeWithText("[ CONFIG ]").performClick()
        composeRule.onNodeWithText("OPERATOR // CONFIG").assertIsDisplayed()
    }

    @Test
    fun navegaACaptura() {
        composeRule.onNodeWithText("[ VOICE ]").performClick()
        composeRule.onNodeWithText("OPERATOR // CAPTURE").assertIsDisplayed()
    }

    @Test
    fun navegaAlHistorial() {
        composeRule.onNodeWithText("[ LOG ]").performClick()
        composeRule.onNodeWithText("OPERATOR // LOG").assertIsDisplayed()
    }
}
