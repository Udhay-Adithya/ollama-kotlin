package org.udhay.ollama

/**
 * Configuration for [OllamaClient].
 */
data class OllamaClientConfig(
    /** Base URL of the Ollama server, e.g. http://127.0.0.1:11434 */
    val host: String? = null,

    /** Headers applied to every request (e.g. Authorization). */
    val headers: Map<String, String> = emptyMap(),
)

