package org.udhay.ollama

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import org.udhay.ollama.internal.applyConfig
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
import java.io.Closeable
import java.nio.file.Path

/**
 * Kotlin client for the [Ollama](https://ollama.com) REST API.
 *
 * Supports both suspend (one-shot) and [Flow]-based (streaming) interaction.
 *
 * ```kotlin
 * val client = OllamaClient()
 * val response = client.chat(ChatRequest(model = "llama3", messages = listOf(...)))
 * client.close()
 * ```
 *
 * Or using the factory DSL:
 * ```kotlin
 * val client = OllamaClient { host = "http://192.168.1.100:11434" }
 * ```
 */
public class OllamaClient(
    private val configProvider: suspend () -> OllamaClientConfig,
    engine: HttpClientEngine? = null,
) : Closeable {

    public constructor(
        config: OllamaClientConfig = OllamaClientConfig(),
        engine: HttpClientEngine? = null,
    ) : this({ config }, engine)

    /**
     * DSL constructor: `OllamaClient { host = "..." }`.
     */
    public constructor(block: OllamaClientConfig.Builder.() -> Unit) : this(
        OllamaClientConfig.Builder().apply(block).build()
    )

    private suspend fun resolveConfig(): OllamaClientConfig {
        val config = configProvider()
        val headers = buildMap {
            putAll(config.headers)
            if (keys.none { it.equals("authorization", ignoreCase = true) }) {
                OllamaEnv.apiKey()?.let { put(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
        return config.copy(headers = headers)
    }

    internal val httpClient: HttpClient = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) { json(DefaultJson) }
    }

    // ---- Generate ----

    /** Generates a completion. Waits for the full response (non-streaming). */
    public suspend fun generate(request: GenerateRequest): GenerateResponse =
        postJsonOrNdjsonLast("/api/generate", request)

    /** Generates a completion, streaming each token as it arrives. */
    public fun generateStream(request: GenerateRequest): Flow<GenerateResponse> = flow {
        val config = resolveConfig()
        emitAll(httpClient.postJsonLines("/api/generate", request.copy(stream = true), DefaultJson, config))
    }

    // ---- Chat ----

    /** Sends a chat request. Waits for the full response (non-streaming). */
    public suspend fun chat(request: ChatRequest): ChatResponse =
        postJsonOrNdjsonLast("/api/chat", request)

    /** Sends a chat request, streaming each message chunk as it arrives. */
    public fun chatStream(request: ChatRequest): Flow<ChatResponse> = flow {
        val config = resolveConfig()
        emitAll(httpClient.postJsonLines("/api/chat", request.copy(stream = true), DefaultJson, config))
    }

    // ---- Embeddings ----

    /** Generates embeddings for the given input. */
    public suspend fun embed(request: EmbedRequest): EmbedResponse =
        httpClient.postJson("/api/embed", request, resolveConfig())

    // ---- Model management ----

    /** Lists all models available on the server. */
    public suspend fun list(): ListResponse = httpClient.getJson("/api/tags", resolveConfig())

    /** Shows metadata about a model. */
    public suspend fun show(request: ShowRequest): ShowResponse =
        httpClient.postJson("/api/show", request, resolveConfig())

    /** Copies a model to a new name. */
    public suspend fun copy(request: CopyRequest): StatusResponse =
        httpClient.postJson("/api/copy", request, resolveConfig())

    /** Deletes a model. */
    public suspend fun delete(request: DeleteRequest): StatusResponse =
        httpClient.deleteJson("/api/delete", request, resolveConfig())

    /** Creates a new model (non-streaming, returns final progress). */
    public suspend fun create(request: CreateRequest): ProgressResponse =
        postJsonOrNdjsonLast("/api/create", request)

    /** Creates a new model, streaming progress updates. */
    public fun createStream(request: CreateRequest): Flow<ProgressResponse> = flow {
        val config = resolveConfig()
        emitAll(httpClient.postJsonLines("/api/create", request.copy(stream = true), DefaultJson, config))
    }

    // ---- Pull / Push ----

    /** Pulls a model from the registry (non-streaming, returns final progress). */
    public suspend fun pull(request: PullRequest): ProgressResponse =
        postJsonOrNdjsonLast("/api/pull", request)

    /** Pulls a model, streaming progress updates. */
    public fun pullStream(request: PullRequest): Flow<ProgressResponse> = flow {
        val config = resolveConfig()
        emitAll(httpClient.postJsonLines("/api/pull", request.copy(stream = true), DefaultJson, config))
    }

    /** Pushes a model to the registry (non-streaming). */
    public suspend fun push(request: PushRequest): ProgressResponse =
        postJsonOrNdjsonLast("/api/push", request)

    /** Pushes a model, streaming progress updates. */
    public fun pushStream(request: PushRequest): Flow<ProgressResponse> = flow {
        val config = resolveConfig()
        emitAll(httpClient.postJsonLines("/api/push", request.copy(stream = true), DefaultJson, config))
    }

    // ---- Blobs ----

    /** Uploads a file to `/api/blobs/{digest}` and returns the digest. */
    public suspend fun createBlob(digest: String, path: Path): String =
        httpClient.uploadBlob(digest, path, resolveConfig())

    /** Computes the SHA-256 digest and uploads the file. Returns the digest. */
    public suspend fun createBlob(path: Path): String {
        val digest = sha256DigestOf(path)
        return createBlob(digest, path)
    }

    // ---- System ----

    /** Lists currently running (loaded) models. */
    public suspend fun ps(): ProcessResponse = httpClient.getJson("/api/ps", resolveConfig())

    /** Returns the Ollama server version. */
    public suspend fun version(): VersionResponse = httpClient.getJson("/api/version", resolveConfig())

    // ---- Web (requires Bearer token against ollama.com) ----

    /** Performs a web search via the Ollama web-search API. */
    public suspend fun webSearch(request: WebSearchRequest): WebSearchResponse =
        httpClient.postJson("/api/web_search", request, resolveConfig())

    /** Fetches a single page via the Ollama web-fetch API. */
    public suspend fun webFetch(request: WebFetchRequest): WebFetchResponse =
        httpClient.postJson("/api/web_fetch", request, resolveConfig())

    // ---- Internals ----

    private suspend inline fun <reified Req : Any, reified Res> postJsonOrNdjsonLast(
        path: String,
        body: Req,
    ): Res {
        val config = resolveConfig()
        val response: HttpResponse = httpClient.post {
            applyConfig(config, path)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        response.requireSuccess()
        return if (response.isNdjson()) response.bodyFromNdjsonLast(DefaultJson) else response.bodyOrThrow()
    }

    override fun close() {
        httpClient.close()
    }
}