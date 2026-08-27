package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.udhay.ollama.api.CreateRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `/api/create` needs either `from` or `files`. Without `files` a model could only be derived from
 * an existing model, which is the wall behind ollama/ollama-js#194.
 */
class CreateFromFilesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun client(engine: MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `files is sent under its wire name`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            val files = body.getValue("files").jsonObject
            assertEquals("sha256:abc123", files.getValue("my-model.gguf").jsonPrimitive.content)
            assertNull(body["from"], "from should be absent when creating from files")
            jsonResponse("""{"status":"success"}""")
        }
        val res = client(engine).use {
            it.create(
                CreateRequest(
                    model = "my-model",
                    files = mapOf("my-model.gguf" to "sha256:abc123"),
                )
            )
        }
        assertEquals("success", res.status)
    }

    @Test
    fun `adapters serialize as a digest map alongside files`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("sha256:aaa", body.getValue("files").jsonObject.getValue("base.gguf").jsonPrimitive.content)
            assertEquals("sha256:bbb", body.getValue("adapters").jsonObject.getValue("lora.gguf").jsonPrimitive.content)
            jsonResponse("""{"status":"success"}""")
        }
        client(engine).use {
            it.create(
                CreateRequest(
                    model = "tuned",
                    files = mapOf("base.gguf" to "sha256:aaa"),
                    adapters = mapOf("lora.gguf" to "sha256:bbb"),
                )
            )
        }
    }

    @Test
    fun `multiple shards are all sent`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val files = DefaultJson.parseToJsonElement(req.bodyText).jsonObject.getValue("files").jsonObject
            assertEquals(3, files.size)
            jsonResponse("""{"status":"success"}""")
        }
        client(engine).use {
            it.create(
                CreateRequest(
                    model = "sharded",
                    files = mapOf(
                        "model-00001-of-00003.gguf" to "sha256:1",
                        "model-00002-of-00003.gguf" to "sha256:2",
                        "model-00003-of-00003.gguf" to "sha256:3",
                    ),
                )
            )
        }
    }

    @Test
    fun `unset files and adapters are omitted entirely`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertNull(body["files"])
            assertNull(body["adapters"])
            assertEquals("llama3", body.getValue("from").jsonPrimitive.content)
            jsonResponse("""{"status":"success"}""")
        }
        client(engine).use { it.create(CreateRequest(model = "derived", fromModel = "llama3")) }
    }

    @Test
    fun `the upload-then-create flow carries the digest through`() = runTest {
        val gguf = (tempDir / "weights.gguf").also { it.writeBytes("abc".toByteArray()) }

        var sentDigest: String? = null
        val engine = MockEngine { req ->
            if (req.url.encodedPath.startsWith("/api/blobs/")) {
                respond("", HttpStatusCode.Created)
            } else {
                val body = DefaultJson.parseToJsonElement(
                    (req.body as io.ktor.http.content.TextContent).text,
                ).jsonObject
                sentDigest = body.getValue("files").jsonObject.getValue("weights.gguf").jsonPrimitive.content
                respond(
                    """{"status":"success"}""",
                    HttpStatusCode.OK,
                    io.ktor.http.headersOf("Content-Type", "application/json"),
                )
            }
        }

        client(engine).use { c ->
            val digest = c.createBlob(gguf)
            c.create(CreateRequest(model = "from-gguf", files = mapOf("weights.gguf" to digest)))
        }

        assertEquals(
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sentDigest,
        )
    }
}
