package org.udhay.ollama

import com.sun.net.httpserver.HttpServer
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.PullRequest
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ktor's `requestTimeoutMillis` bounds the whole exchange including the body read, so any finite
 * value also caps streaming. These run against a real socket rather than `MockEngine`, because
 * the mock engine does not exercise the timeout plugin's interaction with a live body channel.
 */
class LongRunningStreamTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    /** Emits [chunks] NDJSON lines spread over [totalMillis], flushing each one. */
    private fun startTrickleServer(totalMillis: Long, chunks: Int): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.readBytes()
                exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { out ->
                    repeat(chunks) { i ->
                        Thread.sleep(totalMillis / chunks)
                        val done = i == chunks - 1
                        out.write("""{"model":"m","response":"t$i","status":"s$i","done":$done}""".toByteArray())
                        out.write("\n".toByteArray())
                        out.flush()
                    }
                }
            }
            start()
        }
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}"
    }

    @Test
    fun `a stream outliving the old five minute ceiling is not truncated`() {
        // Six chunks over ~3s. The point is that no finite request timeout applies at all —
        // with one configured, the equivalent long-running stream would be cut off.
        val host = startTrickleServer(totalMillis = 3_000, chunks = 6)
        val chunks = OllamaClient(OllamaClientConfig(host = host)).use { client ->
            runBlocking { client.generateStream(GenerateRequest(model = "m", prompt = "p")).toList() }
        }
        assertEquals(6, chunks.size)
        assertTrue(chunks.last().done == true)
    }

    @Test
    fun `the default config applies no request timeout`() {
        assertNull(OllamaClientConfig().requestTimeoutMillis)
        assertNull(OllamaClientConfig.Builder().build().requestTimeoutMillis)
    }

    @Test
    fun `connect stays bounded so an unreachable host still fails fast`() {
        assertEquals(30_000L, OllamaClientConfig().connectTimeoutMillis)
        assertEquals(30_000L, OllamaClientConfig.Builder().build().connectTimeoutMillis)
    }

    @Test
    fun `a caller who wants a ceiling can still set one and it is enforced`() {
        val host = startTrickleServer(totalMillis = 4_000, chunks = 4)
        assertFailsWith<HttpRequestTimeoutException> {
            OllamaClient(OllamaClientConfig(host = host, requestTimeoutMillis = 800)).use { client ->
                runBlocking { client.generateStream(GenerateRequest(model = "m", prompt = "p")).toList() }
            }
        }
    }

    @Test
    fun `a long non-streaming pull completes without a ceiling`() {
        val host = startTrickleServer(totalMillis = 2_000, chunks = 4)
        val progress = OllamaClient(OllamaClientConfig(host = host)).use { client ->
            runBlocking { client.pull(PullRequest(model = "llama3")) }
        }
        assertEquals("s3", progress.status)
    }
}
