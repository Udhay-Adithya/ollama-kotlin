package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.WebFetchRequest
import org.udhay.ollama.api.WebSearchRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `/api/web_search` and `/api/web_fetch` are served by Ollama's cloud, not by a local server, so
 * they must ignore the configured host. Sending them to `127.0.0.1:11434` simply 404s.
 */
class OllamaClientWebRoutingTest {

    private fun capturingEngine(capture: (String) -> Unit) = MockEngine { req ->
        capture(req.url.toString())
        respond("""{"results":[]}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }

    private fun authorizedConfig(webHost: String? = null) = OllamaClientConfig(
        host = "http://192.168.1.50:11434",
        headers = mapOf("Authorization" to "Bearer test-key"),
    ).let { if (webHost != null) it.copy(webHost = webHost) else it }

    @Test
    fun `webSearch goes to ollama_com rather than the configured host`() = runTest {
        var url = ""
        val client = OllamaClient(authorizedConfig(), capturingEngine { url = it })
        client.use { it.webSearch(WebSearchRequest(query = "kotlin coroutines")) }
        assertEquals("https://ollama.com/api/web_search", url)
    }

    @Test
    fun `webFetch goes to ollama_com rather than the configured host`() = runTest {
        var url = ""
        val client = OllamaClient(authorizedConfig(), capturingEngine { url = it })
        client.use { it.webFetch(WebFetchRequest(url = "https://example.com")) }
        assertEquals("https://ollama.com/api/web_fetch", url)
    }

    @Test
    fun `the local host is still used for ordinary endpoints`() = runTest {
        var url = ""
        val engine = MockEngine { req ->
            url = req.url.toString()
            respond("""{"version":"0.6.2"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        OllamaClient(authorizedConfig(), engine).use { it.version() }
        assertEquals("http://192.168.1.50:11434/api/version", url)
    }

    @Test
    fun `webHost is overridable for a proxy or an enterprise gateway`() = runTest {
        var url = ""
        val client = OllamaClient(authorizedConfig(webHost = "https://proxy.internal:8443"), capturingEngine { url = it })
        client.use { it.webSearch(WebSearchRequest(query = "q")) }
        assertEquals("https://proxy.internal:8443/api/web_search", url)
    }

    @Test
    fun `webSearch fails fast without a bearer token instead of returning 401`() = runTest {
        val engine = capturingEngine { }
        val client = OllamaClient(OllamaClientConfig(headers = emptyMap()), engine)
        val ex = assertFailsWith<OllamaException> {
            client.use { it.webSearch(WebSearchRequest(query = "q")) }
        }
        assertTrue(ex.message!!.contains("OLLAMA_API_KEY"), "message should name the env var: ${ex.message}")
    }

    @Test
    fun `webFetch fails fast when the authorization header is not a bearer token`() = runTest {
        val engine = capturingEngine { }
        val client = OllamaClient(
            OllamaClientConfig(headers = mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
            engine,
        )
        assertFailsWith<OllamaException> {
            client.use { it.webFetch(WebFetchRequest(url = "https://example.com")) }
        }
    }

    @Test
    fun `a lowercase authorization header is accepted`() = runTest {
        var url = ""
        val client = OllamaClient(
            OllamaClientConfig(headers = mapOf("authorization" to "Bearer test-key")),
            capturingEngine { url = it },
        )
        client.use { it.webSearch(WebSearchRequest(query = "q")) }
        assertEquals("https://ollama.com/api/web_search", url)
    }
}
