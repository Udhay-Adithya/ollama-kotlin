package org.udhay.ollama

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.ChatResponse
import org.udhay.ollama.api.CopyRequest
import org.udhay.ollama.api.CreateRequest
import org.udhay.ollama.api.DeleteRequest
import org.udhay.ollama.api.EmbedRequest
import org.udhay.ollama.api.EmbedResponse
import org.udhay.ollama.api.GenerateRequest
import org.udhay.ollama.api.GenerateResponse
import org.udhay.ollama.api.ListResponse
import org.udhay.ollama.api.ProcessResponse
import org.udhay.ollama.api.ProgressResponse
import org.udhay.ollama.api.PullRequest
import org.udhay.ollama.api.PushRequest
import org.udhay.ollama.api.ShowRequest
import org.udhay.ollama.api.ShowResponse
import org.udhay.ollama.api.StatusResponse
import org.udhay.ollama.api.VersionResponse
import org.udhay.ollama.api.WebFetchRequest
import org.udhay.ollama.api.WebFetchResponse
import org.udhay.ollama.api.WebSearchRequest
import org.udhay.ollama.api.WebSearchResponse
import org.udhay.ollama.internal.DefaultJson
import org.udhay.ollama.internal.OllamaEnv
import org.udhay.ollama.internal.bodyFromNdjsonLast
import org.udhay.ollama.internal.bodyOrThrow
import org.udhay.ollama.internal.deleteJson
import org.udhay.ollama.internal.getJson
import org.udhay.ollama.internal.isNdjson
import org.udhay.ollama.internal.postJson
import org.udhay.ollama.internal.postJsonLines
import org.udhay.ollama.internal.requireSuccess
import org.udhay.ollama.internal.uploadBlob
import org.udhay.ollama.util.sha256DigestOf
import java.nio.file.Path

class OllamaClient(
    config: OllamaClientConfig = OllamaClientConfig(),
    engine: HttpClientEngine? = null,
) {
    private var scope = CoroutineScope(SupervisorJob())

    private fun currentStreamJob(): Job = scope.coroutineContext[Job]
        ?: error("Streaming scope Job is missing")

    private val baseHost: String = (config.host ?: OllamaEnv.host() ?: "http://127.0.0.1:11434").trimEnd('/')

    private val resolvedHeaders: Map<String, String> = run {
        val headersLower = config.headers.entries.associate { it.key.lowercase() to it.value }
        val hasAuthorization = headersLower.containsKey("authorization")

        if (hasAuthorization) config.headers
        else {
            val apiKey = OllamaEnv.apiKey()
            if (apiKey == null) config.headers
            else config.headers + mapOf(HttpHeaders.Authorization to "Bearer $apiKey")
        }
    }

    internal val httpClient: HttpClient = if (engine == null) {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(DefaultJson)
            }
            defaultRequest {
                url.takeFrom(baseHost)
                resolvedHeaders.forEach { (k, v) -> headers.append(k, v) }
            }
        }
    } else {
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(DefaultJson)
            }
            defaultRequest {
                url.takeFrom(baseHost)
                resolvedHeaders.forEach { (k, v) -> headers.append(k, v) }
            }
        }
    }

    private suspend inline fun <reified Req : Any, reified Res> postJsonOrNdjsonLast(
        path: String,
        body: Req,
    ): Res {
        val response: HttpResponse = httpClient.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        response.requireSuccess()
        return if (response.isNdjson()) response.bodyFromNdjsonLast(DefaultJson) else response.bodyOrThrow()
    }

    suspend fun chat(request: ChatRequest): ChatResponse = postJsonOrNdjsonLast("/api/chat", request)

    fun chatStreaming(request: ChatRequest): Flow<ChatResponse> {
        val body = if (request.stream == true) request else request.copy(stream = true)
        return httpClient.postJsonLines(
            "/api/chat",
            body,
            DefaultJson,
            requestContext = currentStreamJob(),
        )
    }

    suspend fun generate(request: GenerateRequest): GenerateResponse = postJsonOrNdjsonLast("/api/generate", request)

    fun generateStreaming(request: GenerateRequest): Flow<GenerateResponse> {
        val body = if (request.stream == true) request else request.copy(stream = true)
        return httpClient.postJsonLines(
            "/api/generate",
            body,
            DefaultJson,
            requestContext = currentStreamJob(),
        )
    }

    suspend fun embed(request: EmbedRequest): EmbedResponse = httpClient.postJson("/api/embed", request)

    suspend fun list(): ListResponse = httpClient.getJson("/api/tags")

    suspend fun show(request: ShowRequest): ShowResponse = httpClient.postJson("/api/show", request)

    suspend fun copy(request: CopyRequest): StatusResponse = httpClient.postJson("/api/copy", request)

    suspend fun delete(request: DeleteRequest): StatusResponse = httpClient.deleteJson("/api/delete", request)

    suspend fun create(request: CreateRequest): ProgressResponse = postJsonOrNdjsonLast("/api/create", request)

    fun createStreaming(request: CreateRequest): Flow<ProgressResponse> {
        val body = if (request.stream == true) request else request.copy(stream = true)
        return httpClient.postJsonLines(
            "/api/create",
            body,
            DefaultJson,
            requestContext = currentStreamJob(),
        )
    }

    suspend fun pull(request: PullRequest): ProgressResponse = postJsonOrNdjsonLast("/api/pull", request)

    fun pullStreaming(request: PullRequest): Flow<ProgressResponse> {
        val body = if (request.stream == true) request else request.copy(stream = true)
        return httpClient.postJsonLines(
            "/api/pull",
            body,
            DefaultJson,
            requestContext = currentStreamJob(),
        )
    }

    suspend fun push(request: PushRequest): ProgressResponse = postJsonOrNdjsonLast("/api/push", request)

    fun pushStreaming(request: PushRequest): Flow<ProgressResponse> {
        val body = if (request.stream == true) request else request.copy(stream = true)
        return httpClient.postJsonLines(
            "/api/push",
            body,
            DefaultJson,
            requestContext = currentStreamJob(),
        )
    }

    /** Uploads a file to /api/blobs/{digest} and returns the digest. */
    suspend fun createBlob(digest: String, path: Path): String = httpClient.uploadBlob(digest, path)

    /**
     * Uploads a file to /api/blobs/{sha256:...} and returns the digest.
     */
    suspend fun createBlob(path: Path): String {
        val digest = sha256DigestOf(path)
        return createBlob(digest, path)
    }

    suspend fun ps(): ProcessResponse = httpClient.getJson("/api/ps")

    suspend fun version(): VersionResponse = httpClient.getJson("/api/version")

    /**
     * Requires an API key (Authorization: Bearer ...). The host must point to ollama.com.
     */
    suspend fun webSearch(request: WebSearchRequest): WebSearchResponse = httpClient.postJson("/api/web_search", request)

    /**
     * Requires an API key (Authorization: Bearer ...). The host must point to ollama.com.
     */
    suspend fun webFetch(request: WebFetchRequest): WebFetchResponse = httpClient.postJson("/api/web_fetch", request)

    fun abortAllStreams() {
        scope.cancel("aborted")
        scope = CoroutineScope(SupervisorJob())
    }

    fun close() {
        httpClient.close()
        scope.cancel()
    }
}