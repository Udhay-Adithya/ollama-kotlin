package org.udhay.ollama

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.EmbedRequest
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OllamaClientRequestContractTest {

    @Test
    fun `chat forwards logprobs, top_logprobs, format and tools`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/chat", req.encodedPath)
            assertEquals("POST", req.method.value)

            val el = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("llama3", el.getValue("model").jsonPrimitive.content)

            assertEquals(true, el.getValue("logprobs").jsonPrimitive.content.toBoolean())
            assertEquals(3, el.getValue("top_logprobs").jsonPrimitive.content.toInt())

            val format = el.getValue("format").jsonObject
            assertEquals("object", format.getValue("type").jsonPrimitive.content)

            val tools = el.getValue("tools")
            assertNotNull(tools)

            jsonResponse(
                body = "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true}",
            )
        }

        val client = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

        val request = ChatRequest(
            model = "llama3",
            messages = listOf(Message(role = MessageRole.User, content = "hi")),
            logprobs = true,
            topLogprobs = 3,
            format = JsonObject(mapOf("type" to JsonPrimitive("object"))),
            tools = emptyList(),
            stream = false,
        )

        val res = client.chat(request)
        assertEquals(true, res.done)
        assertEquals("ok", res.message?.content)
        client.close()
    }

    @Test
    fun `generate forwards images and format`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/generate", req.encodedPath)

            val el = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("llama3", el.getValue("model").jsonPrimitive.content)
            assertEquals("hi", el.getValue("prompt").jsonPrimitive.content)

            val images = el.getValue("images")
            assertNotNull(images)

            val format = el.getValue("format").jsonObject
            assertEquals("json", format.getValue("type").jsonPrimitive.content)

            jsonResponse(
                body = "{\"model\":\"llama3\",\"response\":\"ok\",\"done\":true}",
            )
        }

        val client = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)
        val response = client.generate(
            GenerateRequest(
                model = "llama3",
                prompt = "hi",
                images = listOf("BASE64IMAGE"),
                format = JsonObject(mapOf("type" to JsonPrimitive("json"))),
                stream = false,
            ),
        )

        assertEquals(true, response.done)
        assertEquals("ok", response.response)
        client.close()
    }

    @Test
    fun `embed forwards arbitrary JsonElement input`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/embed", req.encodedPath)

            val el = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("nomic-embed-text", el.getValue("model").jsonPrimitive.content)

            // input is any JsonElement; upstream clients accept string/array.
            assertEquals("hello", el.getValue("input").jsonPrimitive.content)

            jsonResponse(
                body = "{\"model\":\"nomic-embed-text\",\"embeddings\":[[0.1,0.2]]}",
            )
        }

        val client = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)
        val res = client.embed(
            EmbedRequest(
                model = "nomic-embed-text",
                input = JsonPrimitive("hello"),
            ),
        )

        assertEquals("nomic-embed-text", res.model)
        assertEquals(1, res.embeddings?.size)
        client.close()
    }

    @Test
    fun `non-2xx response throws OllamaException with status and body`() = runTest {
        val engine = mockEngine(DefaultJson) { _ ->
            respond(
                content = "{\"error\":\"nope\"}",
                status = HttpStatusCode.BadRequest,
                headers = io.ktor.http.headersOf("Content-Type", "application/json"),
            )
        }

        val client = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

        val ex = kotlin.test.assertFailsWith<OllamaException> {
            client.generate(GenerateRequest(model = "m", prompt = "p"))
        }

        assertEquals(400, ex.statusCode)
        assertEquals("{\"error\":\"nope\"}", ex.responseBody)
        client.close()
    }
}
