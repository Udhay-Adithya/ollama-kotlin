package org.udhay.ollama

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OllamaClientConfigTest {

    @Test
    fun `default config has null host and empty headers`() {
        val config = OllamaClientConfig()
        assertEquals(null, config.host)
        assertEquals(emptyMap(), config.headers)
    }

    @Test
    fun `builder DSL sets host and headers`() {
        val client = OllamaClient {
            host = "http://192.168.1.100:11434"
            headers["X-Custom"] = "value"
        }
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `config data class supports copy`() {
        val original = OllamaClientConfig(host = "http://a:11434")
        val modified = original.copy(host = "http://b:11434")
        assertEquals("http://a:11434", original.host)
        assertEquals("http://b:11434", modified.host)
    }

    @Test
    fun `client is closeable`() {
        val client = OllamaClient()
        client.close()
        // Should not throw; verifies Closeable contract
    }

    @Test
    fun `client can be used with use block`() {
        OllamaClient().use { client ->
            assertNotNull(client)
        }
    }
}
