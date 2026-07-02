package com.nehonar.operator.core.voice

sealed interface SttEvent {
    data object Ready : SttEvent
    data object SpeechStart : SttEvent
    data class Level(val rmsDb: Float) : SttEvent
    data class Partial(val text: String) : SttEvent
    data object SpeechEnd : SttEvent
    data class FinalResult(val text: String) : SttEvent
    data class Failed(val error: SttError) : SttEvent
}

enum class SttError {
    NO_MATCH,
    NO_SPEECH,
    NETWORK,
    PERMISSION,
    BUSY,
    UNAVAILABLE,
    OTHER,
}
