package org.udhay.ollama.internal

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.udhay.ollama.OllamaClientConfig
import org.udhay.ollama.OllamaException
import java.io.File
import java.nio.file.Path

internal fun HttpRequestBuilder.applyConfig(
    config: OllamaClientConfig,
    path: String,
    host: String = parseHost(config.host ?: OllamaEnv.host()),
) {
    url(host + "/" + path.removePrefix("/"))
    config.headers.forEach { (k, v) -> header(k, v) }
    timeout {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.connectTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }
}

internal suspend inline fun <reified T> HttpClient.getJson(
    path: String,
    config: OllamaClientConfig,
): T {
    val response = get {
        applyConfig(config, path)
    }
    return response.bodyOrThrow()
}

internal suspend inline fun <reified Req : Any, reified Res> HttpClient.postJson(
    path: String,
    body: Req,
    config: OllamaClientConfig,
    host: String = parseHost(config.host ?: OllamaEnv.host()),
): Res {
    val response = post {
        applyConfig(config, path, host)
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return response.bodyOrThrow()
}

internal suspend inline fun <reified Req : Any, reified Res> HttpClient.deleteJson(
    path: String,
    body: Req,
    config: OllamaClientConfig,
): Res {
    val response = delete {
        applyConfig(config, path)
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return response.bodyOrThrow()
}

internal suspend fun HttpResponse.bodyTextSafe(): String =
    runCatching { bodyAsText() }.getOrElse { "" }

internal suspend inline fun <reified Res> HttpResponse.bodyOrThrow(): Res {
    if (status.isSuccess()) {
        return body()
    }

    val text = bodyTextSafe()
    throw OllamaException(
        message = "Request failed (${request.method.value} ${request.url}): HTTP $status",
        statusCode = status.value,
        responseBody = text,
    )
}

internal fun HttpResponse.isNdjson(): Boolean {
    val ct = contentType() ?: return false
    return ct.contentType == "application" && ct.contentSubtype == "x-ndjson"
}

internal suspend fun HttpResponse.requireSuccess() {
    if (status.isSuccess()) return
    val text = bodyTextSafe()
    throw OllamaException(
        message = "Request failed (${request.method.value} ${request.url}): HTTP $status",
        statusCode = status.value,
        responseBody = text,
    )
}

internal suspend inline fun <reified Res> HttpResponse.bodyFromNdjsonLast(json: Json): Res {
    val text = bodyTextSafe()
    var lastNonBlank: String? = null
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) lastNonBlank = trimmed
    }

    val last = lastNonBlank
        ?: throw OllamaException(
            message = "Empty NDJSON response (${request.method.value} ${request.url})",
            statusCode = status.value,
            responseBody = text,
        )

    val element: JsonElement = json.parseToJsonElement(last)
    return json.decodeFromJsonElement(element)
}

/**
 * Sends a POST request and returns a [Flow] that emits each NDJSON line
 * deserialized as [Res]. If any line contains an `"error"` field, an
 * [OllamaException] is thrown immediately.
 */
internal inline fun <reified Req : Any, reified Res> HttpClient.postJsonLines(
    path: String,
    body: Req,
    json: Json,
    config: OllamaClientConfig,
): Flow<Res> = flow {
    preparePost {
        applyConfig(config, path)
        contentType(ContentType.Application.Json)
        setBody(body)
    }.execute { response ->
        if (!response.status.isSuccess()) {
            val text = response.bodyTextSafe()
            throw OllamaException(
                message = "Streaming request failed (POST $path): HTTP ${response.status}",
                statusCode = response.status.value,
                responseBody = text,
            )
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val element = json.parseToJsonElement(trimmed)

            // Check for error in the JSON line
            if (element is JsonObject) {
                val errorField = element["error"]?.jsonPrimitive?.contentOrNull
                if (!errorField.isNullOrBlank()) {
                    throw OllamaException(
                        message = errorField,
                        responseBody = trimmed,
                    )
                }
            }

            emit(json.decodeFromJsonElement<Res>(element))
        }
    }
}

/**
 * Uploads a file to `/api/blobs/{digest}`.
 *
 * Blobs are model weights and routinely run to several gigabytes, so the file is streamed from
 * disk rather than read into a [ByteArray]. [LocalFileContent] sets `Content-Length` from the file
 * size and feeds the request body in chunks, keeping memory flat regardless of file size.
 */
internal suspend fun HttpClient.uploadBlob(
    digest: String,
    path: Path,
    config: OllamaClientConfig,
): String {
    val file = path.toFile()
    if (!file.isFile) {
        throw OllamaException("Blob file does not exist or is not a regular file: $path")
    }

    val response = request {
        method = HttpMethod.Post
        applyConfig(config, "/api/blobs/$digest")
        setBody(FileUploadContent(file))
    }

    response.requireSuccess()

    return digest
}

/**
 * Streams a file as a request body.
 *
 * [contentLength] is taken from the file so the request carries a real `Content-Length` rather than
 * falling back to chunked encoding, and [readFrom] opens a fresh channel per attempt so a retry
 * does not replay a drained one.
 */
private class FileUploadContent(private val file: File) : OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType = ContentType.Application.OctetStream
    override val contentLength: Long = file.length()
    override fun readFrom(): ByteReadChannel = file.readChannel()
}
