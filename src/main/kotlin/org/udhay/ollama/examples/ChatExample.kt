package org.udhay.ollama.examples

import kotlinx.coroutines.runBlocking
import org.udhay.ollama.OllamaClient
import org.udhay.ollama.OllamaClientConfig

/**
 * Minimal runnable example.
 *
 * This will be extended once the chat API is implemented.
 */
fun main() = runBlocking {
    val config = OllamaClientConfig().apply {}
    val client = OllamaClient()
    client.close()
}

