package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.WebSearchRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Requests were previously anonymous, which makes attribution and debugging harder for anyone
 * running a shared Ollama instance behind a proxy.
 */
class UserAgentAndRedirectTest {

    private fun captureUserAgent(config: OllamaClientConfig): String? {
        var userAgent: String? = null
        val engine = MockEngine { req ->
            userAgent = req.headers[HttpHeaders.UserAgent]
            respond("""{"version":"0.6.2"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        kotlinx.coroutines.runBlocking {
            OllamaClient(config, engine).use { it.version() }
        }
        return userAgent
    }

    @Test
    fun `a default user agent identifies the library and version`() {
        val ua = captureUserAgent(OllamaClientConfig(host = "http://localhost:11434"))
        assertNotNull(ua)
        assertTrue(ua.startsWith("ollama-kotlin/"), "unexpected user agent: $ua")
        assertTrue(ua.contains("Kotlin/"), "unexpected user agent: $ua")
    }

    @Test
    fun `the version in the user agent matches the published coordinates`() {
        val ua = captureUserAgent(OllamaClientConfig(host = "http://localhost:11434"))!!
        val version = ua.substringAfter("ollama-kotlin/").substringBefore(" ")
        assertTrue(
            version.matches(Regex("""\d+\.\d+\.\d+""")),
            "version should come from the generated BuildInfo, got '$version'",
        )
    }

    @Test
    fun `a caller supplied user agent wins`() {
        val ua = captureUserAgent(
            OllamaClientConfig(
                host = "http://localhost:11434",
                headers = mapOf(HttpHeaders.UserAgent to "my-app/2.0"),
            )
        )
        assertEquals("my-app/2.0", ua)
    }

    @Test
    fun `a lowercase user-agent override also wins`() {
        val ua = captureUserAgent(
            OllamaClientConfig(
                host = "http://localhost:11434",
                headers = mapOf("user-agent" to "my-app/3.0"),
            )
        )
        assertEquals("my-app/3.0", ua)
    }

    @Test
    fun `web calls carry the user agent too`() = runTest {
        var ua: String? = null
        val engine = MockEngine { req ->
            ua = req.headers[HttpHeaders.UserAgent]
            respond("""{"results":[]}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        OllamaClient(
            OllamaClientConfig(headers = mapOf("Authorization" to "Bearer k")),
            engine,
        ).use { it.webSearch(WebSearchRequest(query = "q")) }
        assertTrue(ua!!.startsWith("ollama-kotlin/"))
    }

    @Test
    fun `redirects are followed by default`() = runTest {
        var hits = 0
        val engine = MockEngine { req ->
            hits++
            if (req.url.encodedPath == "/api/version") {
                respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/moved"))
            } else {
                respond("""{"version":"0.6.2"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        }
        val res = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine).use { it.version() }
        assertEquals("0.6.2", res.version)
        assertEquals(2, hits, "the redirect should have been followed")
    }

    @Test
    fun `redirects can be turned off`() = runTest {
        var hits = 0
        val engine = MockEngine { req ->
            hits++
            respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/moved"))
        }
        val config = OllamaClientConfig(host = "http://localhost:11434", followRedirects = false)
        runCatching { OllamaClient(config, engine).use { it.version() } }
        assertEquals(1, hits, "no redirect should have been followed")
    }
}
