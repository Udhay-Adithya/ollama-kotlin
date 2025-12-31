package org.udhay.ollama

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.internal.OllamaEnv

class OllamaClient(
    config: OllamaClientConfig = OllamaClientConfig(),
) {
    private var scope = CoroutineScope(SupervisorJob())

    private val baseHost: String = (config.host ?: OllamaEnv.host() ?: "http://127.0.0.1:11434").trimEnd('/')

    private val resolvedHeaders: Map<String, String> = run {
        val headersLower = config.headers.entries.associate { it.key.lowercase() to it.value }
        val hasAuthorization = headersLower.containsKey("authorization")

        if (hasAuthorization) config.headers
        else {
            val apiKey = OllamaEnv.apiKey()
            if (apiKey == null) config.headers
            else config.headers + mapOf(HttpHeaders.Authorization to "Bearer $apiKey")
        }
    }

    internal val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(DefaultJson)
        }
        defaultRequest {
            url.takeFrom(baseHost)
            resolvedHeaders.forEach { (k, v) -> headers.append(k, v) }
        }
    }

    fun abortAllStreams() {
        // Cancels any streaming flows tied to the current scope.
        scope.cancel("aborted")
        // Prepare a fresh scope for future streams.
        scope = CoroutineScope(SupervisorJob())
    }

    fun close() {
        httpClient.close()
        scope.cancel()
    }
}
