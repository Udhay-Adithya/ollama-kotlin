package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class OllamaClientDynamicConfigTest {

    @Test
    fun `dynamic config provider is called for each request`() = runBlocking {
        val callCount = AtomicInteger(0)
        
        val engine = MockEngine { request ->
            val count = callCount.incrementAndGet()
            // Verify host in the request matches what we expect for this call
            val expectedHost = "http://host-$count"
            assertEquals(expectedHost, request.url.toString().substringBefore("/api/version"))
            
            respond(
                content = """{"version": "1.0.0"}""",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf("Content-Type", "application/json")
            )
        }

        val client = OllamaClient(configProvider = {
            val count = callCount.get() + 1
            OllamaClientConfig(host = "http://host-$count")
        }, engine = engine)

        client.use {
            // First call
            client.version()
            assertEquals(1, callCount.get())

            // Second call
            client.version()
            assertEquals(2, callCount.get())
        }
    }

    @Test
    fun `dynamic headers are applied to each request`() = runBlocking {
        val callCount = AtomicInteger(0)

        val engine = MockEngine { request ->
            val count = callCount.incrementAndGet()
            assertEquals("value-$count", request.headers["X-Dynamic"])
            
            respond(
                content = """{"version": "1.0.0"}""",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf("Content-Type", "application/json")
            )
        }

        val client = OllamaClient(configProvider = {
            val count = callCount.get() + 1
            OllamaClientConfig(headers = mapOf("X-Dynamic" to "value-$count"))
        }, engine = engine)

        client.use {
            client.version()
            client.version()
        }
        assertEquals(2, callCount.get())
    }
}
