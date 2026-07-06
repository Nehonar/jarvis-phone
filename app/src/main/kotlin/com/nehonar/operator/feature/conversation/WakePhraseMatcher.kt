package com.nehonar.operator.feature.conversation

/**
 * Detecta la frase de activación configurable dentro de lo que se ha oído (con la
 * app en primer plano). Puro y testeable. Devuelve el texto que va DESPUÉS de la
 * frase (para "operador, apunta comprar pan" → "apunta comprar pan"), o cadena
 * vacía si solo se dijo la frase; `null` si la frase no aparece.
 */
object WakePhraseMatcher {

    data class Match(val remainder: String)

    fun match(text: String, phrase: String): Match? {
        val target = phrase.trim().lowercase()
        if (target.isEmpty()) return null
        val lower = text.lowercase()
        val index = lower.indexOf(target)
        if (index < 0) return null
        val after = text.substring((index + target.length).coerceAtMost(text.length))
        // Quita espacios y puntuación de ambos extremos en una pasada (para
        // "operador, apunta X" → "apunta X", no " apunta X").
        return Match(after.trim { it.isWhitespace() || it in PUNCTUATION })
    }

    private val PUNCTUATION = charArrayOf(',', '.', '!', '?', ';', ':').toSet()
}
