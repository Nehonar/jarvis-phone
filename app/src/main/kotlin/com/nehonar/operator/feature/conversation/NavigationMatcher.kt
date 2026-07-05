package com.nehonar.operator.feature.conversation

/** Secciones a las que el usuario puede llegar por voz, sin menú visible. */
enum class OperatorDestination(
    val spokenName: String,
    val keywords: List<String>,
) {
    REMINDERS("los recordatorios", listOf("recordatorio", "recordatorios", "avisos", "alarmas")),
    PREP("la preparación", listOf("preparación", "preparacion", "checklist", "prep", "lista de tareas")),
    MEMORY("la memoria", listOf("memoria", "lo que recuerdas", "lo que sabes de mí", "recuerdos")),
    PLACES("los lugares", listOf("lugares", "sitios", "ubicaciones")),
    HISTORY("el historial", listOf("historial", "mis notas", "las notas")),
    CONSOLE("la consola", listOf("consola")),
    SETTINGS("los ajustes", listOf("ajustes", "configuración", "configuracion", "opciones", "preferencias")),
    HOME("el inicio", listOf("inicio", "pantalla principal", "el principio")),
}

/**
 * Reconoce órdenes de navegación por voz ("ábreme los recordatorios",
 * "muéstrame la memoria") para que el menú siga disponible aunque no sea visible.
 * Puro y testeable. Exige un verbo de apertura además de la sección, para no
 * confundir una orden ("recuérdame X") ni una pregunta ("¿qué recordatorios
 * tengo?") con una navegación.
 */
object NavigationMatcher {

    private val OPEN_TRIGGERS = listOf(
        "abre", "abrir", "ábreme", "abreme", "ábrelo", "abrelo",
        "muéstrame", "muestrame", "muestra", "enséñame", "ensename", "enseña",
        "llévame", "llevame", "vete a", "ve a", "vamos a", "abre la sección",
    )

    fun match(transcript: String): OperatorDestination? {
        val lower = transcript.lowercase().trim()
        if (OPEN_TRIGGERS.none { lower.contains(it) }) return null
        return OperatorDestination.entries.firstOrNull { destination ->
            destination.keywords.any { lower.contains(it) }
        }
    }
}
