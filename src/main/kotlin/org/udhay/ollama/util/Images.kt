package org.udhay.ollama.util

import org.udhay.ollama.OllamaException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Matches a `data:` URI prefix such as `data:image/png;base64,`. */
private val DATA_URI_PREFIX = Regex("""^data:[^;,]*;base64,""", RegexOption.IGNORE_CASE)

/** Extensions that are unambiguously a file reference rather than base64 payload. */
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/**
 * Reads an image file and returns the bare base64 the Ollama wire format expects.
 *
 * ```kotlin
 * Message(
 *     role = MessageRole.User,
 *     content = "What is in this image?",
 *     images = listOf(imageFromPath(Path.of("photo.png"))),
 * )
 * ```
 *
 * @throws OllamaException if the file does not exist or is not a regular file.
 */
public fun imageFromPath(path: Path): String {
    if (!Files.isRegularFile(path)) {
        throw OllamaException("Image file does not exist or is not a regular file: $path")
    }
    return Base64.getEncoder().encodeToString(Files.readAllBytes(path))
}

/** Encodes already-loaded image bytes as base64. */
public fun imageFromBytes(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

/** Reads [input] fully and encodes it as base64. The stream is not closed. */
public fun imageFromStream(input: InputStream): String =
    Base64.getEncoder().encodeToString(input.readBytes())

/**
 * Normalizes a base64 image string, stripping a `data:` URI prefix if present.
 *
 * Browsers and many toolchains hand out `data:image/png;base64,iVBORw0K...`, and passing that
 * through verbatim produces an image the model cannot decode — the failure mode behind
 * ollama/ollama-js#68.
 *
 * @throws OllamaException if [value] is not valid base64 once any prefix is removed.
 */
public fun imageFromBase64(value: String): String {
    val payload = DATA_URI_PREFIX.replace(value.trim(), "")
    try {
        Base64.getDecoder().decode(payload)
    } catch (e: IllegalArgumentException) {
        throw OllamaException(
            "Image data is not valid base64. Pass a path via imageFromPath, or bytes via " +
                "imageFromBytes.",
            cause = e,
        )
    }
    return payload
}

/**
 * Best-effort conversion of a string that may be a file path or may already be base64.
 *
 * Mirrors `ollama-python`'s `Image` coercion: an existing file is read, a string ending in a known
 * image extension is treated as a path (and reported as missing rather than silently sent as
 * garbage), and anything else is validated as base64.
 *
 * Prefer the explicit [imageFromPath] / [imageFromBytes] / [imageFromBase64] when the input type
 * is known — this exists for callers handling user-supplied values of either shape.
 */
public fun image(value: String): String {
    val trimmed = value.trim()

    val asPath = runCatching { Path.of(trimmed) }.getOrNull()
    if (asPath != null && Files.isRegularFile(asPath)) {
        return imageFromPath(asPath)
    }

    if (trimmed.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) {
        throw OllamaException("Image file does not exist: $trimmed")
    }

    return imageFromBase64(trimmed)
}
