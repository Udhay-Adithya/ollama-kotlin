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

    /**
     * Base URL for the hosted web-search API used by [OllamaClient.webSearch] and
     * [OllamaClient.webFetch]. These are served by Ollama's cloud rather than a local server, so
     * they deliberately ignore [host]. Defaults to `https://ollama.com`.
     */
    val webHost: String = "https://ollama.com",

    /**
     * Timeout for the entire request (including connection and data transfer) in milliseconds.
     * Default is 5 minutes.
     */
    val requestTimeoutMillis: Long? = 300_000,

    /** Timeout for establishing a connection in milliseconds. */
    val connectTimeoutMillis: Long? = null,

    /** Timeout for socket read/write operations in milliseconds. */
    val socketTimeoutMillis: Long? = null,
) {
    public class Builder {
        /** Base URL of the Ollama server. `null` falls back to `OLLAMA_HOST` env or `http://127.0.0.1:11434`. */
        public var host: String? = null

        /** Mutable headers map for the DSL. */
        public val headers: MutableMap<String, String> = mutableMapOf()

        /** Base URL for the hosted web-search API. Defaults to `https://ollama.com`. */
        public var webHost: String = "https://ollama.com"

        /** Timeout for the entire request in milliseconds. */
        public var requestTimeoutMillis: Long? = 300_000

        /** Timeout for establishing a connection in milliseconds. */
        public var connectTimeoutMillis: Long? = null

        /** Timeout for socket read/write operations in milliseconds. */
        public var socketTimeoutMillis: Long? = null

        public fun build(): OllamaClientConfig = OllamaClientConfig(
            host = host,
            headers = headers.toMap(),
            webHost = webHost,
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = socketTimeoutMillis,
        )
    }
}

