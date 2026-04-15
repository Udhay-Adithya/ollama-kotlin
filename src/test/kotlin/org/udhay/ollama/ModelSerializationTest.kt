package org.udhay.ollama

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatResponse
import org.udhay.ollama.api.GenerateResponse
import org.udhay.ollama.api.ListResponse
import org.udhay.ollama.api.ProcessResponse
import org.udhay.ollama.api.ShowResponse
import org.udhay.ollama.api.EmbedResponse
import org.udhay.ollama.api.ProgressResponse
import org.udhay.ollama.api.WebSearchResponse
import org.udhay.ollama.api.WebFetchResponse
import org.udhay.ollama.internal.DefaultJson
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelSerializationTest {

    @Test
    fun `ListResponse deserializes with modified_at and details`() {
        val json = """
            {"models":[{"name":"llama3:latest","model":"llama3","modified_at":"2024-06-15T10:30:00Z","size":4700000000,"digest":"sha256:abc","details":{"family":"llama"}}]}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<ListResponse>(json)
        assertEquals(1, res.models.size)
        assertEquals("2024-06-15T10:30:00Z", res.models[0].modifiedAt)
    }

    @Test
    fun `ShowResponse deserializes ModelDetails with camelCase mapping`() {
        val json = """
            {"license":"MIT","details":{"parent_model":"base","format":"gguf","family":"llama","families":["llama"],"parameter_size":"8B","quantization_level":"Q4_0"}}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<ShowResponse>(json)
        assertNotNull(res.details)
        assertEquals("base", res.details?.parentModel)
        assertEquals("8B", res.details?.parameterSize)
        assertEquals("Q4_0", res.details?.quantizationLevel)
    }

    @Test
    fun `ProcessResponse deserializes size_vram and context_length`() {
        val json = """
            {"models":[{"name":"llama3","model":"llama3","size":4000000000,"size_vram":3500000000,"digest":"abc","expires_at":"2025-01-01T00:00:00Z","context_length":4096}]}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<ProcessResponse>(json)
        assertEquals(1, res.models.size)
        assertEquals(3500000000L, res.models[0].sizeVram)
        assertEquals(4096, res.models[0].contextLength)
    }

    @Test
    fun `ChatResponse deserializes timing fields`() {
        val json = """
            {"model":"llama3","created_at":"2025-01-01T00:00:00Z","message":{"role":"assistant","content":"hi"},"done":true,"total_duration":500000000,"load_duration":100000000,"prompt_eval_count":10,"prompt_eval_duration":200000000,"eval_count":5,"eval_duration":200000000}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<ChatResponse>(json)
        assertEquals(500000000L, res.totalDuration)
        assertEquals(10, res.promptEvalCount)
    }

    @Test
    fun `GenerateResponse deserializes thinking field`() {
        val json = """
            {"model":"qwen3","response":"42","done":true,"thinking":"Let me think..."}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<GenerateResponse>(json)
        assertEquals("Let me think...", res.thinking)
    }

    @Test
    fun `EmbedResponse deserializes embeddings`() {
        val json = """
            {"model":"nomic-embed-text","embeddings":[[0.1,0.2,0.3],[0.4,0.5,0.6]]}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<EmbedResponse>(json)
        assertEquals(2, res.embeddings?.size)
        assertEquals(3, res.embeddings?.get(0)?.size)
    }

    @Test
    fun `ProgressResponse deserializes all fields`() {
        val json = """
            {"status":"downloading","digest":"sha256:abc123","total":1000000,"completed":500000}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<ProgressResponse>(json)
        assertEquals("downloading", res.status)
        assertEquals(1000000L, res.total)
        assertEquals(500000L, res.completed)
    }

    @Test
    fun `WebSearchResponse deserializes results`() {
        val json = """
            {"results":[{"title":"Page","url":"https://example.com","content":"text"}]}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<WebSearchResponse>(json)
        assertEquals(1, res.results.size)
        assertEquals("Page", res.results[0].title)
    }

    @Test
    fun `WebFetchResponse deserializes with links`() {
        val json = """
            {"title":"Example","url":"https://example.com","content":"Hello World","links":["https://a.com","https://b.com"]}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<WebFetchResponse>(json)
        assertEquals("Example", res.title)
        assertEquals(2, res.links?.size)
    }

    @Test
    fun `unknown fields are ignored gracefully`() {
        val json = """
            {"model":"llama3","response":"ok","done":true,"some_future_field":"value","another":123}
        """.trimIndent()
        val res = DefaultJson.decodeFromString<GenerateResponse>(json)
        assertEquals("ok", res.response)
    }
}
