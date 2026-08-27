package org.udhay.ollama.integration

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.EmbedRequest
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import org.udhay.ollama.api.Options
import org.udhay.ollama.api.ShowRequest
import org.udhay.ollama.api.tool
import org.udhay.ollama.api.toolCalls
import org.udhay.ollama.internal.DefaultJson
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against a real `ollama serve`.
 *
 * The mock-based suite locks in whatever the client already believes about the wire protocol, so a
 * wrong belief passes its own tests — which is exactly how `chat()` shipped returning empty
 * strings. These exercise the paths callers actually take against a real server.
 *
 * Enable with `OLLAMA_INTEGRATION_TESTS=true`. Override the models with `OLLAMA_TEST_MODEL` and
 * `OLLAMA_TEST_EMBED_MODEL`.
 *
 * These use `runBlocking`, not `runTest`. `runTest` caps the test body at 60 seconds of virtual
 * time, which real generation against a real model routinely exceeds — the model load alone can.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaIntegrationTest {

    private val model get() = IntegrationSupport.chatModel

    @BeforeAll
    fun setUp() {
        IntegrationSupport.ensureModel(IntegrationSupport.chatModel)
        IntegrationSupport.ensureModel(IntegrationSupport.embedModel)
    }

    // ---- The regression that started all this ----

    @Test
    fun `chat returns non-empty content`() = runBlocking {
        val response = IntegrationSupport.client().use {
            it.chat(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "Reply with the single word: hello")),
                    options = Options(temperature = 0.0, seed = 1),
                )
            )
        }
        assertTrue(
            !response.message?.content.isNullOrBlank(),
            "chat() returned empty content — the server streamed and only the final chunk was read",
        )
        assertEquals(true, response.done)
    }

    @Test
    fun `generate returns non-empty response`() = runBlocking {
        val response = IntegrationSupport.client().use {
            it.generate(
                GenerateRequest(
                    model = model,
                    prompt = "Reply with the single word: hello",
                    options = Options(temperature = 0.0, seed = 1),
                )
            )
        }
        assertTrue(!response.response.isNullOrBlank(), "generate() returned empty content")
        assertEquals(true, response.done)
    }

    @Test
    fun `the README quick start produces output`() = runBlocking {
        // Verbatim from the README, so documentation drift shows up here.
        IntegrationSupport.client().use { client ->
            val response = client.chat(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "Why is the sky blue?")),
                )
            )
            assertTrue(!response.message?.content.isNullOrBlank(), "the README example prints an empty line")
        }
    }

    // ---- Streaming ----

    @Test
    fun `chatStream accumulates to a comparable answer`() = runBlocking {
        val chunks = IntegrationSupport.client().use {
            it.chatStream(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "Count: one two three")),
                    options = Options(temperature = 0.0, seed = 1),
                )
            ).toList()
        }
        assertTrue(chunks.size > 1, "expected multiple chunks, got ${chunks.size}")
        assertTrue(chunks.last().done == true, "the final chunk should be marked done")
        val accumulated = chunks.mapNotNull { it.message?.content }.joinToString("")
        assertTrue(accumulated.isNotBlank(), "accumulated stream was empty")
    }

    @Test
    fun `generateStream emits incremental tokens`() = runBlocking {
        val chunks = IntegrationSupport.client().use {
            it.generateStream(GenerateRequest(model = model, prompt = "Say hello")).toList()
        }
        assertTrue(chunks.size > 1)
        assertTrue(chunks.mapNotNull { it.response }.joinToString("").isNotBlank())
    }

    // ---- Structured output, tools, thinking ----

    @Test
    fun `structured output parses as the requested schema`() = runBlocking {
        val response = IntegrationSupport.client().use {
            it.chat(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "List exactly 3 colors.")),
                    format = buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "colors",
                                    buildJsonObject {
                                        put("type", "array")
                                        put("items", buildJsonObject { put("type", "string") })
                                    },
                                )
                            },
                        )
                        put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("colors"))))
                    },
                    options = Options(temperature = 0.0, seed = 1),
                )
            )
        }
        val content = response.message?.content
        assertNotNull(content)
        val parsed = DefaultJson.parseToJsonElement(content).jsonObject
        assertTrue(parsed.containsKey("colors"), "structured output missing the requested key: $content")
    }

    @Test
    fun `tool calling round-trips through the builder`() = runBlocking {
        IntegrationSupport.assumeCapability(model, "tools")

        val addTool = tool("add", "Adds two numbers together") {
            number("a", "First addend")
            number("b", "Second addend")
        }

        val response = IntegrationSupport.client().use {
            it.chat(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "What is 2 + 3? Use the add tool.")),
                    tools = listOf(addTool),
                    options = Options(temperature = 0.0, seed = 1),
                )
            )
        }

        val calls = response.toolCalls
        assertTrue(calls.isNotEmpty(), "expected a tool call, got content: ${response.message?.content}")
        assertEquals("add", calls.first().function?.name)
    }

    @Test
    fun `thinking is returned on the message`() = runBlocking {
        IntegrationSupport.assumeCapability(model, "thinking")

        val response = IntegrationSupport.client().use {
            it.chat(
                ChatRequest(
                    model = model,
                    messages = listOf(Message(role = MessageRole.User, content = "What is 15 * 23 + 47?")),
                    think = JsonPrimitive(true),
                )
            )
        }
        assertTrue(
            !response.message?.thinking.isNullOrBlank(),
            "reasoning should arrive on message.thinking, not the top-level field",
        )
    }

    // ---- Embeddings ----

    @Test
    fun `embed returns vectors of a consistent dimension`() = runBlocking {
        val response = IntegrationSupport.client().use {
            it.embed(
                EmbedRequest(
                    model = IntegrationSupport.embedModel,
                    input = kotlinx.serialization.json.JsonArray(
                        listOf(JsonPrimitive("first text"), JsonPrimitive("second text")),
                    ),
                )
            )
        }
        val embeddings = response.embeddings
        assertNotNull(embeddings)
        assertEquals(2, embeddings.size)
        assertEquals(embeddings[0].size, embeddings[1].size, "vectors should share a dimension")
        assertTrue(embeddings[0].isNotEmpty())
    }

    // ---- Model and server endpoints ----

    @Test
    fun `list show ps and version all succeed`() = runBlocking {
        IntegrationSupport.client().use { client ->
            val models = client.list()
            assertTrue(models.models.isNotEmpty(), "the test model should be present after setup")

            val show = client.show(ShowRequest(model = model))
            assertNotNull(show.details, "show() should return model details")

            client.ps() // must not throw; may legitimately be empty

            assertTrue(client.version().version.isNotBlank())
        }
    }

    @Test
    fun `ping reports the server as reachable`() = runBlocking {
        assertTrue(IntegrationSupport.client().use { it.ping() })
    }

    @Test
    fun `options actually reach the model`() = runBlocking {
        // A seeded, zero-temperature request must be reproducible.
        fun request() = GenerateRequest(
            model = model,
            prompt = "Name one color.",
            options = Options(temperature = 0.0, seed = 42, numPredict = 20),
        )

        IntegrationSupport.client().use { client ->
            val first = client.generate(request()).response
            val second = client.generate(request()).response
            assertEquals(first, second, "seeded deterministic generation should repeat exactly")
        }
    }
}
