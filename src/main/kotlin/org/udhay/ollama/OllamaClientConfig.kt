package org.udhay.ollama

/**
 * Configuration for [OllamaClient].
 *
 * Can be built directly or via the [Builder] DSL:
 * ```kotlin
 * OllamaClient {
 *     host = "http://192.168.1.100:11434"
 *     headers["X-Custom"] = "value"
 * }
 * ```
 */
public data class OllamaClientConfig(
    /** Base URL of the Ollama server, e.g. `http://127.0.0.1:11434`. */
    val host: String? = null,

    /** Headers applied to every request (e.g. Authorization). */
    val headers: Map<String, String> = emptyMap(),
) {
    public class Builder {
        /** Base URL of the Ollama server. `null` falls back to `OLLAMA_HOST` env or `http://127.0.0.1:11434`. */
        public var host: String? = null

        /** Mutable headers map for the DSL. */
        public val headers: MutableMap<String, String> = mutableMapOf()

        public fun build(): OllamaClientConfig = OllamaClientConfig(
            host = host,
            headers = headers.toMap(),
        )
    }
}

