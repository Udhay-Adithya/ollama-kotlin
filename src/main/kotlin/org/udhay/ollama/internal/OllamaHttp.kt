package org.udhay.ollama.internal

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByteArray
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.udhay.ollama.OllamaException
import java.nio.file.Files
import java.nio.file.Path

internal suspend inline fun <reified T> HttpClient.getJson(path: String): T {
    val response = get(path)
    return response.bodyOrThrow()
}

internal suspend inline fun <reified Req : Any, reified Res> HttpClient.postJson(path: String, body: Req): Res {
    val response = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return response.bodyOrThrow()
}

internal suspend inline fun <reified Req : Any, reified Res> HttpClient.deleteJson(path: String, body: Req): Res {
    val response = delete(path) {
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
    // Ktor's ContentType may contain charset params. Compare the base type/subtype only.
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

internal inline fun <reified Req : Any, reified Res> HttpClient.postJsonLines(
    path: String,
    body: Req,
    json: Json,
): Flow<Res> = flow {
    val response = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
        timeout { requestTimeoutMillis = 0 }
    }

    response.requireSuccess()

    val channel: ByteReadChannel = response.body()
    val buffer = StringBuilder()

    while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
        val chunk = channel.readAvailableBytesOrNull() ?: break
        buffer.append(chunk.decodeToString())

        while (true) {
            val newlineIdx = buffer.indexOf("\n")
            if (newlineIdx < 0) break

            val line = buffer.take(newlineIdx).toString().trim()
            buffer.delete(0, newlineIdx + 1)

            if (line.isBlank()) continue

            val element: JsonElement = json.parseToJsonElement(line)
            emit(json.decodeFromJsonElement(element))
        }
    }

    val trailing = buffer.toString().trim()
    if (trailing.isNotBlank()) {
        val element: JsonElement = json.parseToJsonElement(trailing)
        emit(json.decodeFromJsonElement(element))
    }
}

private suspend fun ByteReadChannel.readAvailableBytesOrNull(): ByteArray? {
    if (isClosedForRead) return null

    // Use a bounded read to avoid deprecated APIs and keep memory use predictable.
    // This suspends until at least one byte is available or the channel is closed.
    val bytes = runCatching { readByteArray(8 * 1024) }.getOrNull() ?: return null
    return if (bytes.isEmpty()) null else bytes
}

internal suspend fun HttpClient.uploadBlob(
    digest: String,
    path: Path,
): String {
    val bytes = Files.readAllBytes(path)

    val response = request {
        method = HttpMethod.Post
        url {
            encodedPath = "/api/blobs/$digest"
        }
        contentType(ContentType.Application.OctetStream)
        setBody(bytes)
    }

    response.requireSuccess()

    return digest
}
