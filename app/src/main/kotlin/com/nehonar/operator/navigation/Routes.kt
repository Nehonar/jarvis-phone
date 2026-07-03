package com.nehonar.operator.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object CaptureRoute

@Serializable
data class ReviewRoute(val voiceNoteId: String)

@Serializable
data object HistoryRoute

@Serializable
data object ConsoleRoute

@Serializable
data object SettingsRoute
