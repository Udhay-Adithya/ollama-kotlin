package org.udhay.ollama

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.GenerateResponse
import org.udhay.ollama.api.ShowRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import org.udhay.ollama.testutil.ndjsonResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Image-generation models need `width`/`height`/`steps` on the way in and return `image` plus step
 * progress on the way out. Without the response fields the generated image is silently discarded
 * by `ignoreUnknownKeys`.
 */
class ImageGenerationFieldsTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `width height and steps are sent`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("1024", body.getValue("width").jsonPrimitive.content)
            assertEquals("768", body.getValue("height").jsonPrimitive.content)
            assertEquals("30", body.getValue("steps").jsonPrimitive.content)
            jsonResponse("""{"model":"m","done":true,"image":"aGVsbG8="}""")
        }
        val res = client(engine).use {
            it.generate(
                GenerateRequest(model = "m", prompt = "a cat", width = 1024, height = 768, steps = 30)
            )
        }
        assertEquals("aGVsbG8=", res.image)
    }

    @Test
    fun `the fields are omitted when unset`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertNull(body["width"])
            assertNull(body["height"])
            assertNull(body["steps"])
            jsonResponse("""{"model":"m","response":"ok","done":true}""")
        }
        client(engine).use { it.generate(GenerateRequest(model = "m", prompt = "p")) }
    }

    @Test
    fun `the generated image is deserialized`() {
        val res = DefaultJson.decodeFromString<GenerateResponse>(
            """{"model":"m","done":true,"image":"iVBORw0KGgo="}""",
        )
        assertEquals("iVBORw0KGgo=", res.image)
    }

    @Test
    fun `step progress survives a streaming round trip`() = runTest {
        val engine = mockEngine(DefaultJson) { _ ->
            ndjsonResponse(
                listOf(
                    """{"model":"m","done":false,"completed":10,"total":30}""",
                    """{"model":"m","done":false,"completed":20,"total":30}""",
                    """{"model":"m","done":true,"completed":30,"total":30,"image":"aGVsbG8="}""",
                )
            )
        }
        val chunks = client(engine).use { c ->
            c.generateStream(GenerateRequest(model = "m", prompt = "a cat", steps = 30)).toList()
        }
        assertEquals(listOf(10, 20, 30), chunks.mapNotNull { it.completed })
        assertEquals(30, chunks.last().total)
        assertEquals("aGVsbG8=", chunks.last().image)
    }

    @Test
    fun `show sends verbose when asked`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("true", body.getValue("verbose").jsonPrimitive.content)
            jsonResponse("""{"license":"MIT","model_info":{"tokenizer.ggml.tokens":["a","b"]}}""")
        }
        val res = client(engine).use { it.show(ShowRequest(model = "llama3", verbose = true)) }
        assertEquals(
            2,
            res.modelInfo!!.jsonObject.getValue("tokenizer.ggml.tokens")
                .let { it as kotlinx.serialization.json.JsonArray }.size,
        )
    }

    @Test
    fun `show omits verbose when unset`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertNull(DefaultJson.parseToJsonElement(req.bodyText).jsonObject["verbose"])
            jsonResponse("""{"license":"MIT"}""")
        }
        client(engine).use { it.show(ShowRequest(model = "llama3")) }
    }
}
