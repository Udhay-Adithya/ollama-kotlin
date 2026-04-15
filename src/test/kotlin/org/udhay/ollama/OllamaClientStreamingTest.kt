package org.udhay.ollama

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.api.PullRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.ndjsonResponse
import org.udhay.ollama.testutil.mockEngine
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaClientStreamingTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `chatStream emits each NDJSON chunk`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/chat", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("true", body.getValue("stream").jsonPrimitive.content)
            ndjsonResponse(
                listOf(
                    """{"model":"llama3","message":{"role":"assistant","content":"Hello"},"done":false}""",
                    """{"model":"llama3","message":{"role":"assistant","content":" world"},"done":false}""",
                    """{"model":"llama3","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}""",
                )
            )
        }
        val chunks = client(engine).use { c ->
            c.chatStream(
                ChatRequest(model = "llama3", messages = listOf(Message(role = MessageRole.User, content = "hi")))
            ).toList()
        }
        assertEquals(3, chunks.size)
        assertEquals("Hello", chunks[0].message?.content)
        assertEquals(" world", chunks[1].message?.content)
        assertEquals(true, chunks[2].done)
    }

    @Test
    fun `generateStream emits each NDJSON chunk`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/generate", req.encodedPath)
            ndjsonResponse(
                listOf(
                    """{"model":"llama3","response":"Why","done":false}""",
                    """{"model":"llama3","response":" not?","done":false}""",
                    """{"model":"llama3","response":"","done":true,"done_reason":"stop"}""",
                )
            )
        }
        val chunks = client(engine).use { c ->
            c.generateStream(GenerateRequest(model = "llama3", prompt = "joke")).toList()
        }
        assertEquals(3, chunks.size)
        assertEquals("Why", chunks[0].response)
        assertEquals(true, chunks[2].done)
    }

    @Test
    fun `pullStream emits progress updates`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/pull", req.encodedPath)
            ndjsonResponse(
                listOf(
                    """{"status":"pulling manifest"}""",
                    """{"status":"downloading","digest":"sha256:abc","total":1000,"completed":250}""",
                    """{"status":"downloading","digest":"sha256:abc","total":1000,"completed":1000}""",
                    """{"status":"success"}""",
                )
            )
        }
        val updates = client(engine).use { c ->
            c.pullStream(PullRequest(model = "llama3")).toList()
        }
        assertEquals(4, updates.size)
        assertEquals("pulling manifest", updates[0].status)
        assertEquals(250L, updates[1].completed)
        assertEquals("success", updates[3].status)
    }

    @Test
    fun `streaming error in NDJSON throws OllamaException`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            ndjsonResponse(
                listOf(
                    """{"model":"llama3","response":"ok","done":false}""",
                    """{"error":"model not found"}""",
                )
            )
        }
        val ex = assertFailsWith<OllamaException> {
            client(engine).use { c ->
                c.generateStream(GenerateRequest(model = "llama3", prompt = "hi")).toList()
            }
        }
        assertTrue(ex.message!!.contains("model not found"))
    }
}
