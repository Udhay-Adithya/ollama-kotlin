package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.udhay.ollama.internal.parseHost
import kotlin.test.assertEquals

/**
 * Ollama documents `OLLAMA_HOST` in scheme-less forms such as `127.0.0.1:11434`, so the host has
 * to be normalized before a path is appended to it.
 */
class HostParserTest {

    @Test
    fun `blank and null hosts fall back to the local default`() {
        assertEquals("http://127.0.0.1:11434", parseHost(null))
        assertEquals("http://127.0.0.1:11434", parseHost(""))
        assertEquals("http://127.0.0.1:11434", parseHost("   "))
    }

    @Test
    fun `a scheme-less host gains http and the default port`() {
        assertEquals("http://1.2.3.4:11434", parseHost("1.2.3.4"))
        assertEquals("http://example.com:11434", parseHost("example.com"))
        assertEquals("http://localhost:11434", parseHost("localhost"))
    }

    @Test
    fun `a scheme-less host with a port keeps that port`() {
        assertEquals("http://1.2.3.4:56789", parseHost("1.2.3.4:56789"))
        assertEquals("http://example.com:56789", parseHost("example.com:56789"))
        assertEquals("http://localhost:11434", parseHost("localhost:11434"))
    }

    @Test
    fun `a bare port binds to the loopback address`() {
        assertEquals("http://127.0.0.1:56789", parseHost(":56789"))
    }

    @Test
    fun `an explicit scheme is preserved`() {
        assertEquals("http://1.2.3.4:56789", parseHost("http://1.2.3.4:56789"))
        assertEquals("https://example.com:56789", parseHost("https://example.com:56789"))
        assertEquals("http://127.0.0.1:11434", parseHost("http://127.0.0.1:11434"))
    }

    /**
     * Deliberately unlike `ollama-python`, which rewrites these to `:80` and `:443`. Some gateways
     * match on the `Host` header and reject the redundant port — see ollama/ollama-python#222.
     */
    @Test
    fun `an explicit scheme without a port leaves the port implicit`() {
        assertEquals("http://example.com", parseHost("http://example.com"))
        assertEquals("https://example.com", parseHost("https://example.com"))
        assertEquals("https://1.2.3.4", parseHost("https://1.2.3.4"))
    }

    @Test
    fun `a trailing slash is dropped and any other path is preserved`() {
        assertEquals("http://example.com:11434", parseHost("example.com/"))
        assertEquals("http://example.com:56789", parseHost("example.com:56789/"))
        assertEquals("http://example.com:11434/path", parseHost("example.com/path"))
        assertEquals("http://example.com:56789/path", parseHost("example.com:56789/path"))
        assertEquals("https://example.com:56789/path", parseHost("https://example.com:56789/path"))
        assertEquals("http://example.com:56789/path", parseHost("example.com:56789/path/"))
        assertEquals("https://example.com/ollama", parseHost("https://example.com/ollama"))
    }

    @Test
    fun `IPv6 literals keep their brackets`() {
        assertEquals("http://[0001:002:003:0004::1]:11434", parseHost("[0001:002:003:0004::1]"))
        assertEquals("http://[0001:002:003:0004::1]:56789", parseHost("[0001:002:003:0004::1]:56789"))
        assertEquals("https://[0001:002:003:0004::1]:56789", parseHost("https://[0001:002:003:0004::1]:56789"))
        assertEquals("http://[0001:002:003:0004::1]:11434/path", parseHost("[0001:002:003:0004::1]/path"))
        assertEquals("http://[0001:002:003:0004::1]:56789/path", parseHost("[0001:002:003:0004::1]:56789/path"))
        assertEquals("http://[::1]:11434", parseHost("[::1]"))
    }

    @Test
    fun `a trailing colon with no digits is treated as part of the address`() {
        assertEquals("http://example.com::11434", parseHost("example.com:"))
    }

    @Test
    fun `the normalized host reaches the actual request URL`() = runTest {
        suspend fun urlFor(host: String): String {
            var captured = ""
            val engine = MockEngine { req ->
                captured = req.url.toString()
                respond("""{"version":"1"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
            OllamaClient(OllamaClientConfig(host = host), engine).use { it.version() }
            return captured
        }

        assertEquals("http://localhost:11434/api/version", urlFor("localhost:11434"))
        assertEquals("http://127.0.0.1:11434/api/version", urlFor("127.0.0.1:11434"))
        assertEquals("http://example.com:11434/api/version", urlFor("example.com"))
        assertEquals("https://example.com/ollama/api/version", urlFor("https://example.com/ollama"))
    }
}
