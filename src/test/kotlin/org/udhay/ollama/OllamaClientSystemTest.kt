package org.udhay.ollama

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OllamaClientSystemTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `ping returns true when server is running`() = runTest {
        val engine = io.ktor.client.engine.mock.MockEngine { _ ->
            respond("Ollama is running", HttpStatusCode.OK)
        }
        assertTrue(client(engine).ping())
    }

    @Test
    fun `ping returns false when server returns error`() = runTest {
        val engine = io.ktor.client.engine.mock.MockEngine { _ ->
            respond("Internal Server Error", HttpStatusCode.InternalServerError)
        }
        assertFalse(client(engine).ping())
    }

    @Test
    fun `ping returns false when connection fails`() = runTest {
        val engine = io.ktor.client.engine.mock.MockEngine { _ ->
            throw Exception("Connection refused")
        }
        assertFalse(client(engine).ping())
    }

    @Test
    fun `ps returns running models`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/ps", req.encodedPath)
            assertEquals(HttpMethod.Get, req.method)
            jsonResponse(
                """{"models":[{"name":"llama3:latest","model":"llama3","size":4000000000,"size_vram":4000000000,"digest":"abc123","expires_at":"2025-01-01T00:00:00Z","details":{"family":"llama","parameter_size":"8B","quantization_level":"Q4_0"}}]}"""
            )
        }
        val res = client(engine).use { it.ps() }
        assertEquals(1, res.models.size)
        assertEquals("llama3:latest", res.models[0].name)
        assertEquals(4000000000L, res.models[0].sizeVram)
    }

    @Test
    fun `version returns server version`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/version", req.encodedPath)
            assertEquals(HttpMethod.Get, req.method)
            jsonResponse("""{"version":"0.6.2"}""")
        }
        val res = client(engine).use { it.version() }
        assertEquals("0.6.2", res.version)
    }
}
