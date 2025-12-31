package org.udhay.ollama.examples

import kotlinx.coroutines.runBlocking
import org.udhay.ollama.OllamaClient
import org.udhay.ollama.OllamaClientConfig
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole

/**
 * Minimal runnable example.
 *
 * This will be extended once the chat API is implemented.
 */
fun main() = runBlocking {
    val client = OllamaClient(
        OllamaClientConfig(
            host = "http://0.0.0.0:11434",
        )
    )

    val models = client.list()
    print(models)

    val response = client.chat(
        ChatRequest(
            model = "granite3.2-vision",
            messages = listOf(
                Message(role = MessageRole.User, content = "Say hello in one short sentence."),
            ),
        )
    )

    println(response.message?.content ?: response.error)

    client.close()
}
