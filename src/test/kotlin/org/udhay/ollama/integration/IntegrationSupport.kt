package org.udhay.ollama.integration

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.udhay.ollama.OllamaClient
import org.udhay.ollama.OllamaClientConfig
import org.udhay.ollama.api.PullRequest
import org.udhay.ollama.api.ShowRequest

/**
 * Shared setup for the integration suite.
 *
 * These tests talk to a real `ollama serve`. They are opt-in via `OLLAMA_INTEGRATION_TESTS=true`
 * so that contributors without a server, and the ordinary PR build, are unaffected.
 */
internal object IntegrationSupport {

    /** Small instruct model used for chat, generate, tools and thinking. */
    val chatModel: String = System.getenv("OLLAMA_TEST_MODEL")?.takeIf { it.isNotBlank() } ?: "qwen3:0.6b"

    /** Small embedding model; the chat model does not necessarily have embedding capability. */
    val embedModel: String =
        System.getenv("OLLAMA_TEST_EMBED_MODEL")?.takeIf { it.isNotBlank() } ?: "all-minilm"

    val host: String? = System.getenv("OLLAMA_HOST")?.takeIf { it.isNotBlank() }

    fun client(): OllamaClient = OllamaClient(OllamaClientConfig(host = host))

    /** Downloads [model] if the server does not already have it. Streams so CI logs show progress. */
    fun ensureModel(model: String) = runBlocking {
        client().use { c ->
            val present = c.list().models.any { it.name == model || it.model == model }
            if (!present) {
                println("integration: pulling $model")
                c.pullStream(PullRequest(model = model)).collect { progress ->
                    progress.status?.let { println("integration: $model — $it") }
                }
            }
        }
    }

    /**
     * Skips the calling test when the model lacks a capability, rather than failing.
     *
     * Tool calling and thinking are model-dependent, so a suite pinned to a different
     * `OLLAMA_TEST_MODEL` should degrade to skipped rather than red.
     */
    fun assumeCapability(model: String, capability: String) = runBlocking {
        val capabilities = client().use { it.show(ShowRequest(model = model)).capabilities.orEmpty() }
        assumeTrue(
            capability in capabilities,
            "$model does not advertise '$capability' (has: $capabilities)",
        )
    }
}
