package org.udhay.ollama

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.CopyRequest
import org.udhay.ollama.api.CreateRequest
import org.udhay.ollama.api.DeleteRequest
import org.udhay.ollama.api.PullRequest
import org.udhay.ollama.api.PushRequest
import org.udhay.ollama.api.ShowRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import org.udhay.ollama.testutil.ndjsonResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OllamaClientModelManagementTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `list returns available models`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/tags", req.encodedPath)
            assertEquals(HttpMethod.Get, req.method)
            jsonResponse(
                """{"models":[{"name":"llama3:latest","model":"llama3","modified_at":"2024-01-01","size":4000000000,"digest":"abc123"}]}"""
            )
        }
        val res = client(engine).use { it.list() }
        assertEquals(1, res.models.size)
        assertEquals("llama3:latest", res.models[0].name)
        assertEquals("abc123", res.models[0].digest)
    }

    @Test
    fun `show returns model metadata`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/show", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("llama3", body.getValue("model").jsonPrimitive.content)
            jsonResponse(
                """{"license":"MIT","modelfile":"FROM llama3","details":{"family":"llama","parameter_size":"8B","quantization_level":"Q4_0"},"capabilities":["completion"]}"""
            )
        }
        val res = client(engine).use { it.show(ShowRequest(model = "llama3")) }
        assertEquals("MIT", res.license)
        assertNotNull(res.details)
        assertEquals("llama", res.details?.family)
        assertEquals("8B", res.details?.parameterSize)
        assertEquals("Q4_0", res.details?.quantizationLevel)
    }

    @Test
    fun `copy sends source and destination`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/copy", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("llama3", body.getValue("source").jsonPrimitive.content)
            assertEquals("my-llama3", body.getValue("destination").jsonPrimitive.content)
            jsonResponse("""{"status":"success"}""")
        }
        val res = client(engine).use { it.copy(CopyRequest(source = "llama3", destination = "my-llama3")) }
        assertEquals("success", res.status)
    }

    @Test
    fun `delete sends model name`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/delete", req.encodedPath)
            assertEquals("DELETE", req.method.value)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("old-model", body.getValue("model").jsonPrimitive.content)
            jsonResponse("""{"status":"success"}""")
        }
        val res = client(engine).use { it.delete(DeleteRequest(model = "old-model")) }
        assertEquals("success", res.status)
    }

    @Test
    fun `pull returns final progress via NDJSON`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/pull", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("llama3", body.getValue("model").jsonPrimitive.content)
            ndjsonResponse(
                listOf(
                    """{"status":"pulling manifest"}""",
                    """{"status":"downloading","digest":"sha256:abc","total":1000,"completed":500}""",
                    """{"status":"success"}""",
                )
            )
        }
        val res = client(engine).use { it.pull(PullRequest(model = "llama3")) }
        assertEquals("success", res.status)
    }

    @Test
    fun `push returns final progress via NDJSON`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/push", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("my-model", body.getValue("model").jsonPrimitive.content)
            ndjsonResponse(
                listOf(
                    """{"status":"pushing manifest"}""",
                    """{"status":"success"}""",
                )
            )
        }
        val res = client(engine).use { it.push(PushRequest(model = "my-model")) }
        assertEquals("success", res.status)
    }

    @Test
    fun `create returns final progress via NDJSON`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/create", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("custom-model", body.getValue("model").jsonPrimitive.content)
            assertEquals("llama3", body.getValue("from").jsonPrimitive.content)
            ndjsonResponse(
                listOf(
                    """{"status":"reading model"}""",
                    """{"status":"success"}""",
                )
            )
        }
        val res = client(engine).use {
            it.create(CreateRequest(model = "custom-model", fromModel = "llama3", system = "You are helpful."))
        }
        assertEquals("success", res.status)
    }
}
