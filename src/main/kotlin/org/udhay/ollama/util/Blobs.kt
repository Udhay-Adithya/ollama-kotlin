package org.udhay.ollama.util

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Computes the `sha256:<hex>` digest of a file.
 *
 * The file is read in chunks to handle large files without excessive memory use.
 *
 * @param path Path to the file.
 * @return A string in the format `"sha256:<64-char hex>"` (e.g. `"sha256:ba7816..."`).
 */
public fun sha256DigestOf(path: Path): String {
    Files.newInputStream(path).use { input ->
        return sha256DigestOf(input)
    }
}

/**
 * Computes the `sha256:<hex>` digest of bytes read from [input].
 *
 * @param input An [InputStream] to read.
 * @return A string in the format `"sha256:<64-char hex>"`.
 */
public fun sha256DigestOf(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        md.update(buffer, 0, read)
    }
    val hash = md.digest()
    val hex = hash.joinToString("") { b -> "%02x".format(b) }
    return "sha256:$hex"
}
