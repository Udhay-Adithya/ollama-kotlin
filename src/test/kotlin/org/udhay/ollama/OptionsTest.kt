package org.udhay.ollama

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.EmbedRequest
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.api.Options
import org.udhay.ollama.api.OptionsSerializer
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Options` used to be a value class no request type accepted — unreachable public API. It now
 * carries typed fields, with [Options.extra] flattened into the same object for forward
 * compatibility with options newer than this library.
 */
class OptionsTest {

    private fun encode(options: Options) =
        DefaultJson.encodeToString(OptionsSerializer, options).let { DefaultJson.parseToJsonElement(it).jsonObject }

    @Test
    fun `only the fields that were set are emitted`() {
        val json = encode(Options(temperature = 0.7, topK = 40))
        assertEquals(2, json.size, "unset options must be omitted, got $json")
        assertEquals("0.7", json.getValue("temperature").jsonPrimitive.content)
        assertEquals("40", json.getValue("top_k").jsonPrimitive.content)
    }

    @Test
    fun `an empty Options emits an empty object`() {
        assertEquals(0, encode(Options()).size)
    }

    @Test
    fun `snake_case wire names are used`() {
        val json = encode(
            Options(
                numCtx = 8192,
                numPredict = 256,
                repeatLastN = 64,
                topP = 0.9,
                mirostatTau = 5.0,
                penalizeNewline = false,
                useMlock = true,
            )
        )
        assertEquals(
            setOf("num_ctx", "num_predict", "repeat_last_n", "top_p", "mirostat_tau", "penalize_newline", "use_mlock"),
            json.keys,
        )
    }

    @Test
    fun `stop sequences serialize as an array`() {
        val json = encode(Options(stop = listOf("\n\n", "END")))
        assertEquals(2, json.getValue("stop").let { it as kotlinx.serialization.json.JsonArray }.size)
    }

    @Test
    fun `extra options are flattened alongside the typed ones`() {
        val json = encode(
            Options(
                temperature = 0.2,
                extra = mapOf("some_new_option" to JsonPrimitive(true)),
            )
        )
        assertEquals("0.2", json.getValue("temperature").jsonPrimitive.content)
        assertEquals("true", json.getValue("some_new_option").jsonPrimitive.content)
        assertNull(json["extra"], "extra must not appear as a nested key")
    }

    @Test
    fun `a round trip puts unknown keys back into extra`() {
        val original = Options(temperature = 0.5, extra = mapOf("future_flag" to JsonPrimitive(7)))
        val decoded = DefaultJson.decodeFromString(
            OptionsSerializer,
            DefaultJson.encodeToString(OptionsSerializer, original),
        )
        assertEquals(0.5, decoded.temperature)
        assertEquals("7", decoded.extra.getValue("future_flag").jsonPrimitive.content)
    }

    @Test
    fun `chat sends options as a flat object`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val options = DefaultJson.parseToJsonElement(req.bodyText).jsonObject.getValue("options").jsonObject
            assertEquals("0.7", options.getValue("temperature").jsonPrimitive.content)
            assertEquals("8192", options.getValue("num_ctx").jsonPrimitive.content)
            assertEquals("42", options.getValue("seed").jsonPrimitive.content)
            jsonResponse("""{"model":"llama3","message":{"role":"assistant","content":"ok"},"done":true}""")
        }
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine).use {
            it.chat(
                ChatRequest(
                    model = "llama3",
                    messages = listOf(Message(role = MessageRole.User, content = "hi")),
                    options = Options(temperature = 0.7, numCtx = 8192, seed = 42),
                )
            )
        }
    }

    @Test
    fun `generate and embed accept options too`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertTrue(body.containsKey("options"), "options should be present on ${req.encodedPath}")
            if (req.encodedPath == "/api/embed") {
                jsonResponse("""{"model":"m","embeddings":[[0.1]]}""")
            } else {
                jsonResponse("""{"model":"m","response":"ok","done":true}""")
            }
        }
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine).use { c ->
            c.generate(GenerateRequest(model = "m", prompt = "p", options = Options(seed = 1)))
            c.embed(EmbedRequest(model = "m", input = JsonPrimitive("hi"), options = Options(numCtx = 512)))
        }
    }

    @Test
    fun `an omitted options field leaves the key out of the request`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertNull(DefaultJson.parseToJsonElement(req.bodyText).jsonObject["options"])
            jsonResponse("""{"model":"m","response":"ok","done":true}""")
        }
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine).use {
            it.generate(GenerateRequest(model = "m", prompt = "p"))
        }
    }
}
