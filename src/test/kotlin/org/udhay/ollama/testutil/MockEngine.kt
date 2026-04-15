package org.udhay.ollama.testutil

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.client.request.HttpResponseData
import kotlinx.serialization.json.Json

/**
 * Convenience data class capturing the essential parts of a mock request.
 */
data class MockRequest(
    val method: io.ktor.http.HttpMethod,
    val encodedPath: String,
    val bodyText: String,
)

/**
 * Creates a Ktor [MockEngine] that decodes the request body for easy assertions.
 */
fun mockEngine(
    @Suppress("UNUSED_PARAMETER") json: Json,
    handler: MockRequestHandleScope.(MockRequest) -> HttpResponseData,
): MockEngine = MockEngine { requestData: HttpRequestData ->
    val bodyText = when (val body = requestData.body) {
        is TextContent -> body.text
        else -> ""
    }

    val mockReq = MockRequest(
        method = requestData.method,
        encodedPath = requestData.url.encodedPath,
        bodyText = bodyText,
    )

    handler(mockReq)
}

/**
 * Simple JSON response helper for mock engine handlers.
 */
fun MockRequestHandleScope.jsonResponse(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

/**
 * NDJSON (newline-delimited JSON) response helper for streaming mock handlers.
 * Each element in [lines] should be a valid JSON string.
 */
fun MockRequestHandleScope.ndjsonResponse(
    lines: List<String>,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = lines.joinToString("\n"),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
)
