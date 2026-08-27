package org.udhay.ollama

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.CreateRequest
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.api.PullRequest
import org.udhay.ollama.api.PushRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import org.udhay.ollama.testutil.ndjsonResponse
import kotlin.test.assertEquals

/**
 * `/api/chat` and `/api/generate` stream by default: omitting `stream` means `stream: true`.
 * The one-shot methods must therefore send `"stream": false` explicitly, otherwise the server
 * replies with NDJSON whose final chunk carries empty content.
 */
class OllamaClientNonStreamingDefaultTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    private fun streamFlagOf(body: String): Boolean? =
        DefaultJson.parseToJsonElement(body).jsonObject["stream"]?.jsonPrimitive?.content?.toBoolean()

    @Test
    fun `chat sends stream false when the request leaves it unset`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals(false, streamFlagOf(req.bodyText), "chat() must send \"stream\": false")
            jsonResponse("""{"model":"llama3","message":{"role":"assistant","content":"The sky is blue."},"done":true}""")
        }
        val res = client(engine).use {
            it.chat(ChatRequest(model = "llama3", messages = listOf(Message(role = MessageRole.User, content = "why?"))))
        }
        assertEquals("The sky is blue.", res.message?.content)
    }

    @Test
    fun `generate sends stream false when the request leaves it unset`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals(false, streamFlagOf(req.bodyText), "generate() must send \"stream\": false")
            jsonResponse("""{"model":"llama3","response":"The sky is blue.","done":true}""")
        }
        val res = client(engine).use { it.generate(GenerateRequest(model = "llama3", prompt = "why?")) }
        assertEquals("The sky is blue.", res.response)
    }

    @Test
    fun `chat overrides an explicit stream true rather than returning an empty final chunk`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals(false, streamFlagOf(req.bodyText))
            jsonResponse("""{"model":"llama3","message":{"role":"assistant","content":"full"},"done":true}""")
        }
        val res = client(engine).use {
            it.chat(
                ChatRequest(
                    model = "llama3",
                    messages = listOf(Message(role = MessageRole.User, content = "hi")),
                    stream = true,
                )
            )
        }
        assertEquals("full", res.message?.content)
    }

    @Test
    fun `generate overrides an explicit stream true`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals(false, streamFlagOf(req.bodyText))
            jsonResponse("""{"model":"llama3","response":"full","done":true}""")
        }
        val res = client(engine).use {
            it.generate(GenerateRequest(model = "llama3", prompt = "hi", stream = true))
        }
        assertEquals("full", res.response)
    }

    @Test
    fun `pull push and create send stream false`() = runTest {
        suspend fun assertStreamFalse(path: String, call: suspend OllamaClient.() -> Unit) {
            val engine = mockEngine(DefaultJson) { req ->
                assertEquals(path, req.encodedPath)
                assertEquals(false, streamFlagOf(req.bodyText), "$path must send \"stream\": false")
                jsonResponse("""{"status":"success"}""")
            }
            client(engine).use { it.call() }
        }

        assertStreamFalse("/api/pull") { pull(PullRequest(model = "llama3")) }
        assertStreamFalse("/api/push") { push(PushRequest(model = "my-model")) }
        assertStreamFalse("/api/create") { create(CreateRequest(model = "custom", fromModel = "llama3")) }
    }

    @Test
    fun `an NDJSON reply is still collapsed to its final chunk for older servers`() = runTest {
        val engine = mockEngine(DefaultJson) { _ ->
            ndjsonResponse(
                listOf(
                    """{"status":"pulling manifest"}""",
                    """{"status":"success"}""",
                )
            )
        }
        val res = client(engine).use { it.pull(PullRequest(model = "llama3")) }
        assertEquals("success", res.status)
    }

    @Test
    fun `streaming methods still send stream true`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals(true, streamFlagOf(req.bodyText), "chatStream() must send \"stream\": true")
            ndjsonResponse(listOf("""{"model":"llama3","message":{"role":"assistant","content":"hi"},"done":true}"""))
        }
        client(engine).use { c ->
            c.chatStream(
                ChatRequest(model = "llama3", messages = listOf(Message(role = MessageRole.User, content = "hi"))),
            ).toList()
        }
    }
}
