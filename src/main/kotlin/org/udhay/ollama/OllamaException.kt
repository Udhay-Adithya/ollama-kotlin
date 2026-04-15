package org.udhay.ollama

/**
 * Thrown when the Ollama server returns a non-success status or when the response cannot be processed.
 */
public class OllamaException(
    message: String,
    public val statusCode: Int? = null,
    public val responseBody: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

