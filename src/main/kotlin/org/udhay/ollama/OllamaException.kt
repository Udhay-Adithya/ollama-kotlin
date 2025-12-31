package org.udhay.ollama

/**
 * Thrown when the Ollama server returns a non-success status or when the response cannot be processed.
 */
class OllamaException(
    message: String,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

