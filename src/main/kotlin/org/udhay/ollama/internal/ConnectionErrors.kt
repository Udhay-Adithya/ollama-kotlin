package org.udhay.ollama.internal

import org.udhay.ollama.OllamaException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

private const val DOWNLOAD_HINT = "https://ollama.com/download"

/**
 * Translates low-level connection failures into an [OllamaException] that says what to do about it.
 *
 * A raw `ConnectException: Connection refused` gives no indication that Ollama is the thing that
 * needs starting, and `UnresolvedAddressException` carries no message at all — it prints as a bare
 * class name. `ollama-python` maps the same conditions to a single actionable string.
 *
 * Anything that is not a connection failure is returned unchanged so it propagates as-is.
 */
internal fun mapConnectionError(cause: Throwable, url: String): Throwable = when (cause) {
    is ConnectException, is NoRouteToHostException ->
        OllamaException(
            "Failed to connect to Ollama at $url. Check that Ollama is installed, running and " +
                "reachable — see $DOWNLOAD_HINT",
            cause = cause,
        )

    is UnknownHostException, is UnresolvedAddressException ->
        OllamaException(
            "Could not resolve the Ollama host in $url. Check the host in OllamaClientConfig or " +
                "the OLLAMA_HOST environment variable.",
            cause = cause,
        )

    else -> cause
}
