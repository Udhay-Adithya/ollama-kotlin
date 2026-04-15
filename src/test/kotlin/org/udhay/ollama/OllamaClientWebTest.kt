package org.udhay.ollama

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.WebFetchRequest
import org.udhay.ollama.api.WebSearchRequest
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.testutil.jsonResponse
import org.udhay.ollama.testutil.mockEngine
import kotlin.test.assertEquals

class OllamaClientWebTest {

    private fun client(engine: io.ktor.client.engine.mock.MockEngine) =
        OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

    @Test
    fun `webSearch sends query and max_results`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/web_search", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("kotlin coroutines", body.getValue("query").jsonPrimitive.content)
            assertEquals("5", body.getValue("max_results").jsonPrimitive.content)
            jsonResponse(
                """{"results":[{"title":"Kotlin Coroutines","url":"https://kotlinlang.org","content":"Coroutines are..."}]}"""
            )
        }
        val res = client(engine).use {
            it.webSearch(WebSearchRequest(query = "kotlin coroutines", maxResults = 5))
        }
        assertEquals(1, res.results.size)
        assertEquals("Kotlin Coroutines", res.results[0].title)
    }

    @Test
    fun `webFetch sends url`() = runTest {
        val engine = mockEngine(DefaultJson) { req ->
            assertEquals("/api/web_fetch", req.encodedPath)
            val body = DefaultJson.parseToJsonElement(req.bodyText).jsonObject
            assertEquals("https://example.com", body.getValue("url").jsonPrimitive.content)
            jsonResponse(
                """{"title":"Example","url":"https://example.com","content":"Hello","links":["https://example.com/a"]}"""
            )
        }
        val res = client(engine).use {
            it.webFetch(WebFetchRequest(url = "https://example.com"))
        }
        assertEquals("Example", res.title)
        assertEquals("Hello", res.content)
        assertEquals(1, res.links?.size)
    }
}
