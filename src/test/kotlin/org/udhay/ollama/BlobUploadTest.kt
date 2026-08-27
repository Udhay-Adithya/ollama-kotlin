package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Blobs are model weights and routinely run to several gigabytes, so the upload must stream from
 * disk rather than materialise a `ByteArray`.
 */
class BlobUploadTest {

    @TempDir
    lateinit var tempDir: Path

    private fun client(engine: MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `the request body streams rather than buffering the file`() = runTest {
        val file = (tempDir / "blob.bin").also { it.writeBytes(ByteArray(64 * 1024) { i -> (i % 251).toByte() }) }

        var isStreaming = false
        var bodyType: String? = null
        var declaredLength: Long? = null
        val engine = MockEngine { req ->
            val body = req.body
            // ByteArrayContent would mean the whole file was materialised in memory.
            isStreaming = body is OutgoingContent.ReadChannelContent
            bodyType = body::class.simpleName
            declaredLength = body.contentLength
            respond("", HttpStatusCode.Created)
        }

        client(engine).use { it.createBlob("sha256:abc", file) }

        assertTrue(isStreaming, "body should be a streaming ReadChannelContent, was $bodyType")
        assertEquals(65536L, declaredLength, "Content-Length should come from the file size")
    }

    @Test
    fun `the uploaded bytes match the file exactly`() = runTest {
        val payload = ByteArray(4096) { i -> (i % 97).toByte() }
        val file = (tempDir / "exact.bin").also { it.writeBytes(payload) }

        var received: ByteArray? = null
        val engine = MockEngine { req ->
            val content = req.body as OutgoingContent.ReadChannelContent
            received = content.readFrom().readRemaining().readByteArray()
            respond("", HttpStatusCode.Created)
        }

        client(engine).use { it.createBlob("sha256:def", file) }
        assertTrue(payload.contentEquals(received), "uploaded bytes should match the file")
    }

    @Test
    fun `the digest is computed and returned when not supplied`() = runTest {
        // sha256("abc")
        val file = (tempDir / "abc.bin").also { it.writeBytes("abc".toByteArray()) }
        var path = ""
        val engine = MockEngine { req ->
            path = req.url.encodedPath
            respond("", HttpStatusCode.Created)
        }

        val digest = client(engine).use { it.createBlob(file) }
        assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
        assertEquals("/api/blobs/$digest", path)
    }

    @Test
    fun `a missing file fails with a clear message instead of an IO error`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Created) }
        val missing = tempDir / "does-not-exist.bin"

        val ex = assertFailsWith<OllamaException> {
            client(engine).use { it.createBlob("sha256:abc", missing) }
        }
        assertTrue(ex.message!!.contains("does not exist"), "message was: ${ex.message}")
    }

    @Test
    fun `a directory is rejected rather than treated as a blob`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Created) }
        assertFailsWith<OllamaException> {
            client(engine).use { it.createBlob("sha256:abc", tempDir) }
        }
    }

    @Test
    fun `an empty file still uploads with a zero content length`() = runTest {
        val file = (tempDir / "empty.bin").also { it.createFile() }
        var declaredLength: Long? = null
        val engine = MockEngine { req ->
            declaredLength = req.body.contentLength
            respond("", HttpStatusCode.Created)
        }
        client(engine).use { it.createBlob("sha256:empty", file) }
        assertEquals(0L, declaredLength)
    }
}
