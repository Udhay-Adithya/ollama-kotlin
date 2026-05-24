package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import kotlin.test.assertTrue

class OllamaClientTimeoutTest {

    @Test
    fun `client should throw HttpRequestTimeoutException when request exceeds timeout`() = runTest {
        val mockEngine = MockEngine { _ ->
            delay(1000) // Simulate a slow response
            respond("{}", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }

        val config = OllamaClientConfig(
            host = "http://localhost:11434",
            requestTimeoutMillis = 500 // Set a short timeout
        )

        val client = OllamaClient(config, mockEngine)

        assertThrows<HttpRequestTimeoutException> {
            client.chat(ChatRequest(model = "llama3", messages = listOf(Message(role = MessageRole.User, content = "hi"))))
        }
    }

    @Test
    fun `client should succeed when request completes within timeout`() = runTest {
        val mockEngine = MockEngine { _ ->
            delay(200) // Simulate a fast-enough response
            respond(
                "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"done\":true}",
                HttpStatusCode.OK,
                headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val config = OllamaClientConfig(
            host = "http://localhost:11434",
            requestTimeoutMillis = 1000 // Set a generous timeout
        )

        val client = OllamaClient(config, mockEngine)

        val response = client.chat(ChatRequest(model = "llama3", messages = listOf(Message(role = MessageRole.User, content = "hi"))))
        assertTrue(response.done == true)
    }
}
