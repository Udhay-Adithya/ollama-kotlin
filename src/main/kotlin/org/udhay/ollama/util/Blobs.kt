package org.udhay.ollama.util

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * `sha256:<hex>` digest of the file contents.
 */
fun sha256DigestOf(path: Path): String {
    Files.newInputStream(path).use { input ->
        return sha256DigestOf(input)
    }
}

/**
 * `sha256:<hex>` digest of the bytes streamed from [input].
 */
fun sha256DigestOf(input: InputStream): String {
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
