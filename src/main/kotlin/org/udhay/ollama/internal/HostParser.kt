package org.udhay.ollama.internal

/** Port Ollama listens on when the host does not name one. */
private const val DEFAULT_OLLAMA_PORT = 11434

/** Address used when the host names only a port, or nothing at all. */
private const val DEFAULT_OLLAMA_ADDRESS = "127.0.0.1"

/**
 * Normalizes a host string into an absolute base URL.
 *
 * Ollama documents `OLLAMA_HOST` in forms that omit the scheme (`127.0.0.1:11434`), so a host
 * cannot be concatenated with a path directly — doing so yields URLs such as
 * `localhost://localhost/11434/api/chat`.
 *
 * Rules:
 * - a blank or `null` host resolves to `http://127.0.0.1:11434`
 * - a host with no scheme gets `http://` and, if it names no port, `:11434`
 * - a host that names only a port (`:56789`) is bound to `127.0.0.1`
 * - a host that already carries a scheme keeps it, and keeps its port **implicit** when none is
 *   given — an explicit `:80`/`:443` is not appended, because some gateways match on the `Host`
 *   header and reject the redundant port
 * - IPv6 literals keep their square brackets
 * - a trailing slash is dropped; any other path is preserved as a prefix
 *
 * ```
 * null                        -> http://127.0.0.1:11434
 * "1.2.3.4"                   -> http://1.2.3.4:11434
 * ":56789"                    -> http://127.0.0.1:56789
 * "1.2.3.4:56789"             -> http://1.2.3.4:56789
 * "example.com/path/"         -> http://example.com:11434/path
 * "https://example.com"       -> https://example.com
 * "https://example.com:56789" -> https://example.com:56789
 * "[::1]:56789"               -> http://[::1]:56789
 * ```
 */
internal fun parseHost(host: String?): String {
    val raw = host?.trim().orEmpty()
    if (raw.isEmpty()) return "http://$DEFAULT_OLLAMA_ADDRESS:$DEFAULT_OLLAMA_PORT"

    val schemeSeparator = raw.indexOf("://")
    val hasScheme = schemeSeparator >= 0
    val scheme = if (hasScheme) raw.substring(0, schemeSeparator).lowercase() else "http"
    val remainder = if (hasScheme) raw.substring(schemeSeparator + 3) else raw

    val (authority, path) = splitAuthorityAndPath(remainder)
    val (address, port) = splitAddressAndPort(authority)

    // An explicit scheme implies its own default port; only a scheme-less host needs 11434 added.
    val resolvedPort = port ?: if (hasScheme) null else DEFAULT_OLLAMA_PORT
    val resolvedAddress = address.ifEmpty { DEFAULT_OLLAMA_ADDRESS }

    return buildString {
        append(scheme).append("://").append(resolvedAddress)
        if (resolvedPort != null) append(':').append(resolvedPort)
        if (path.isNotEmpty()) append('/').append(path)
    }
}

/** Splits `host:port/some/path` into its authority and its slash-trimmed path. */
private fun splitAuthorityAndPath(value: String): Pair<String, String> {
    // Skip past an IPv6 literal so its colons and the closing bracket are not mistaken for a path.
    val searchFrom = if (value.startsWith("[")) value.indexOf(']').let { if (it < 0) 0 else it } else 0
    val slash = value.indexOf('/', startIndex = searchFrom)
    return if (slash < 0) {
        value to ""
    } else {
        value.substring(0, slash) to value.substring(slash).trim('/')
    }
}

/** Splits an authority into its address and port, tolerating IPv6 literals and a bare `:port`. */
private fun splitAddressAndPort(authority: String): Pair<String, Int?> {
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return authority to null
        val address = authority.substring(0, close + 1)
        val rest = authority.substring(close + 1)
        val port = rest.removePrefix(":").toIntOrNull().takeIf { rest.startsWith(":") }
        return address to port
    }

    val colon = authority.lastIndexOf(':')
    if (colon < 0) return authority to null

    val port = authority.substring(colon + 1).toIntOrNull() ?: return authority to null
    return authority.substring(0, colon) to port
}
