@file:Suppress("DEPRECATION")

package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.udhay.ollama.api.EmbeddingsRequest
import org.udhay.ollama.api.Options
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `HEAD /api/blobs/:digest` avoids re-uploading gigabytes the server already holds, and
 * `/api/embeddings` is kept for servers predating `/api/embed`.
 */
class BlobExistsAndLegacyEmbeddingsTest {

    @TempDir
    lateinit var tempDir: Path

    private fun client(engine: MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `blobExists issues a HEAD and maps 200 to true`() = runTest {
        var method = ""
        var path = ""
        val engine = MockEngine { req ->
            method = req.method.value
            path = req.url.encodedPath
            respond("", HttpStatusCode.OK)
        }
        assertTrue(client(engine).use { it.blobExists("sha256:abc") })
        assertEquals("HEAD", method)
        assertEquals("/api/blobs/sha256:abc", path)
    }

    @Test
    fun `blobExists maps 404 to false`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        assertFalse(client(engine).use { it.blobExists("sha256:missing") })
    }

    @Test
    fun `blobExists surfaces other failures rather than reporting absence`() = runTest {
        val engine = MockEngine { respond("boom", HttpStatusCode.InternalServerError) }
        val ex = assertFailsWith<OllamaException> { client(engine).use { it.blobExists("sha256:abc") } }
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `createBlob skips the upload when the server already holds the blob`() = runTest {
        val file = (tempDir / "weights.gguf").also { it.writeBytes("abc".toByteArray()) }
        val methods = mutableListOf<String>()
        val engine = MockEngine { req ->
            methods += req.method.value
            respond("", HttpStatusCode.OK)
        }

        val digest = client(engine).use { it.createBlob(file) }
        assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
        assertEquals(listOf("HEAD"), methods, "a present blob must not be re-uploaded")
    }

    @Test
    fun `skipIfPresent false always uploads`() = runTest {
        val file = (tempDir / "weights.gguf").also { it.writeBytes("abc".toByteArray()) }
        val methods = mutableListOf<String>()
        val engine = MockEngine { req ->
            methods += req.method.value
            respond("", HttpStatusCode.OK)
        }

        client(engine).use { it.createBlob(file, skipIfPresent = false) }
        assertEquals(listOf("POST"), methods, "the existence probe should be skipped entirely")
    }

    @Test
    fun `embeddings posts prompt to the legacy endpoint`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/embeddings", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("nomic-embed-text", body.getValue("model").jsonPrimitive.content)
            assertEquals("hello", body.getValue("prompt").jsonPrimitive.content)
            jsonResponse("""{"embedding":[0.1,0.2,0.3]}""")
        }
        val res = client(engine).use {
            it.embeddings(EmbeddingsRequest(model = "nomic-embed-text", prompt = "hello"))
        }
        assertEquals(3, res.embedding?.size)
        assertEquals(0.1, res.embedding?.first())
    }

    @Test
    fun `the legacy response is a single vector, not a list of vectors`() {
        // The shape difference from EmbedResponse is what confuses people in ollama/ollama-js#228.
        val res = DefaultJson.decodeFromString(
            org.udhay.ollama.api.EmbeddingsResponse.serializer(),
            """{"embedding":[0.5,0.6]}""",
        )
        assertEquals(listOf(0.5, 0.6), res.embedding)
    }

    @Test
    fun `embeddings forwards options`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val options = DefaultJson.parseToJsonElement(req.bodyText).jsonObject.getValue("options").jsonObject
            assertEquals("512", options.getValue("num_ctx").jsonPrimitive.content)
            jsonResponse("""{"embedding":[0.1]}""")
        }
        client(engine).use {
            it.embeddings(EmbeddingsRequest(model = "m", prompt = "hi", options = Options(numCtx = 512)))
        }
    }

    @Test
    fun `a HEAD probe carries configured headers`() = runTest {
        var auth: String? = null
        val engine = MockEngine { req ->
            auth = req.headers["X-Token"]
            respond("", HttpStatusCode.OK, headersOf())
        }
        OllamaClient(
            OllamaClientConfig(host = "http://localhost:11434", headers = mapOf("X-Token" to "abc")),
            engine,
        ).use { it.blobExists("sha256:abc") }
        assertEquals("abc", auth)
    }
}
