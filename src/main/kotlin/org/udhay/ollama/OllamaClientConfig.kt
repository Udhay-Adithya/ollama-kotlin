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
     * Whether to follow HTTP redirects. Defaults to `true`, matching `ollama-python`.
     *
     * Ktor decides this when the client is built rather than per request, so it is read from the
     * config passed at construction. With the `configProvider` constructor, pass it there instead —
     * a later value returned by the provider has no effect.
     */
    val followRedirects: Boolean = true,

    /**
     * Base URL for the hosted web-search API used by [OllamaClient.webSearch] and
     * [OllamaClient.webFetch]. These are served by Ollama's cloud rather than a local server, so
     * they deliberately ignore [host]. Defaults to `https://ollama.com`.
     */
    val webHost: String = "https://ollama.com",

    /**
     * Timeout for the entire request/response exchange in milliseconds, or `null` for no ceiling.
     *
     * Defaults to `null`. This bound covers the body read as well, so any finite value also caps
     * streaming: a `pullStream()` of a large model or a long `chatStream()` would be cut off
     * mid-flight once it elapsed. `ollama-python` likewise defaults to no request timeout.
     *
     * Set it only for calls you genuinely want bounded, and prefer [socketTimeoutMillis] to detect
     * a stalled connection — that one measures inactivity rather than total duration.
     */
    val requestTimeoutMillis: Long? = null,

    /**
     * Timeout for establishing a connection in milliseconds. Defaults to 30 seconds so an
     * unreachable host still fails fast even though [requestTimeoutMillis] is unbounded.
     */
    val connectTimeoutMillis: Long? = 30_000,

    /**
     * Maximum inactivity between bytes in milliseconds, or `null` for none. Defaults to `null`.
     *
     * Unlike [requestTimeoutMillis] this does not cap total duration, so it is the right knob for
     * catching a stalled stream. Leave generous headroom: a cold model load can pause for minutes
     * before the first token arrives.
     */
    val socketTimeoutMillis: Long? = null,
) {
    public class Builder {
        /** Base URL of the Ollama server. `null` falls back to `OLLAMA_HOST` env or `http://127.0.0.1:11434`. */
        public var host: String? = null

        /** Mutable headers map for the DSL. */
        public val headers: MutableMap<String, String> = mutableMapOf()

        /** Whether to follow HTTP redirects. Defaults to `true`. */
        public var followRedirects: Boolean = true

        /** Base URL for the hosted web-search API. Defaults to `https://ollama.com`. */
        public var webHost: String = "https://ollama.com"

        /** Timeout for the entire request in milliseconds, or `null` for no ceiling (default). */
        public var requestTimeoutMillis: Long? = null

        /** Timeout for establishing a connection in milliseconds. Defaults to 30 seconds. */
        public var connectTimeoutMillis: Long? = 30_000

        /** Maximum inactivity between bytes in milliseconds, or `null` for none (default). */
        public var socketTimeoutMillis: Long? = null

        public fun build(): OllamaClientConfig = OllamaClientConfig(
            host = host,
            headers = headers.toMap(),
            followRedirects = followRedirects,
            webHost = webHost,
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            socketTimeoutMillis = socketTimeoutMillis,
        )
    }
}

