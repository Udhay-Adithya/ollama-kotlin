package org.udhay.ollama

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.ChatResponse
import org.udhay.ollama.api.GenerateResponse
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import org.udhay.ollama.testutil.ndjsonResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `/api/chat` returns reasoning on the message object; `/api/generate` returns it at the top level.
 * Before `Message.thinking` existed the chat form was dropped by `ignoreUnknownKeys`.
 */
class ThinkingDeserializationTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `chat reasoning is read from the message object`() = runTest {
        val engine = mockEngine(DefaultJson) { _ ->
            jsonResponse(
                """{"model":"qwen3","message":{"role":"assistant","content":"392","thinking":"15 * 23 = 345, plus 47 = 392"},"done":true}"""
            )
        }
        val res = client(engine).use {
            it.chat(
                ChatRequest(
                    model = "qwen3",
                    messages = listOf(Message(role = MessageRole.User, content = "15 * 23 + 47")),
                    think = JsonPrimitive(true),
                )
            )
        }
        assertEquals("15 * 23 = 345, plus 47 = 392", res.message?.thinking)
        assertEquals("392", res.message?.content)
    }

    @Test
    fun `chat reasoning survives streaming chunks`() = runTest {
        val engine = mockEngine(DefaultJson) { _ ->
            ndjsonResponse(
                listOf(
                    """{"model":"qwen3","message":{"role":"assistant","content":"","thinking":"first "},"done":false}""",
                    """{"model":"qwen3","message":{"role":"assistant","content":"","thinking":"second"},"done":false}""",
                    """{"model":"qwen3","message":{"role":"assistant","content":"392"},"done":true}""",
                )
            )
        }
        val chunks = client(engine).use { c ->
            c.chatStream(
                ChatRequest(
                    model = "qwen3",
                    messages = listOf(Message(role = MessageRole.User, content = "hi")),
                    think = JsonPrimitive("high"),
                )
            ).toList()
        }
        assertEquals("first second", chunks.mapNotNull { it.message?.thinking }.joinToString(""))
    }

    @Test
    fun `generate reasoning stays at the top level`() {
        val json = """{"model":"qwen3","response":"392","done":true,"thinking":"Let me think..."}"""
        val res = DefaultJson.decodeFromString<GenerateResponse>(json)
        assertEquals("Let me think...", res.thinking)
    }

    @Test
    fun `a chat reply without reasoning leaves both fields null`() {
        val json = """{"model":"llama3","message":{"role":"assistant","content":"hi"},"done":true}"""
        val res = DefaultJson.decodeFromString<ChatResponse>(json)
        assertNull(res.message?.thinking)
        assertNull(res.thinking)
    }

    @Test
    fun `a thinking level serializes as sent`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("max", body.getValue("think").jsonPrimitive.content)
            jsonResponse("""{"model":"qwen3","message":{"role":"assistant","content":"ok"},"done":true}""")
        }
        client(engine).use {
            it.chat(
                ChatRequest(
                    model = "qwen3",
                    messages = listOf(Message(role = MessageRole.User, content = "hi")),
                    think = JsonPrimitive("max"),
                )
            )
        }
    }

    @Test
    fun `thinking round-trips when a message is sent back as history`() {
        val message = Message(role = MessageRole.Assistant, content = "392", thinking = "reasoning here")
        val encoded = DefaultJson.encodeToString(Message.serializer(), message)
        assertEquals("reasoning here", DefaultJson.decodeFromString(Message.serializer(), encoded).thinking)
    }
}
