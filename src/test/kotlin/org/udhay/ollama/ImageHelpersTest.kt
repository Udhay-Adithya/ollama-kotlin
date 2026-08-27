package org.udhay.ollama

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.udhay.ollama.util.image
import org.udhay.ollama.util.imageFromBase64
import org.udhay.ollama.util.imageFromBytes
import org.udhay.ollama.util.imageFromPath
import org.udhay.ollama.util.imageFromStream
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The wire format is bare base64. Callers previously had to encode by hand, and a `data:` URI
 * pasted straight through produces an undecodable image — ollama/ollama-js#68.
 */
class ImageHelpersTest {

    @TempDir
    lateinit var tempDir: Path

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val pngBase64 = Base64.getEncoder().encodeToString(pngBytes)

    @Test
    fun `a file is read and base64 encoded`() {
        val file = (tempDir / "photo.png").also { it.writeBytes(pngBytes) }
        assertEquals(pngBase64, imageFromPath(file))
    }

    @Test
    fun `bytes are encoded directly`() {
        assertEquals(pngBase64, imageFromBytes(pngBytes))
    }

    @Test
    fun `a stream is read fully`() {
        assertEquals(pngBase64, imageFromStream(pngBytes.inputStream()))
    }

    @Test
    fun `a data uri prefix is stripped`() {
        assertEquals(pngBase64, imageFromBase64("data:image/png;base64,$pngBase64"))
        assertEquals(pngBase64, imageFromBase64("data:image/jpeg;base64,$pngBase64"))
        assertEquals(pngBase64, imageFromBase64("DATA:IMAGE/PNG;BASE64,$pngBase64"))
    }

    @Test
    fun `bare base64 passes through unchanged`() {
        assertEquals(pngBase64, imageFromBase64(pngBase64))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(pngBase64, imageFromBase64("  $pngBase64  "))
    }

    @Test
    fun `invalid base64 is rejected with an actionable message`() {
        val ex = assertFailsWith<OllamaException> { imageFromBase64("not!valid!base64!") }
        assertTrue(ex.message!!.contains("imageFromPath"), ex.message!!)
    }

    @Test
    fun `a missing file is reported rather than silently encoded`() {
        val ex = assertFailsWith<OllamaException> { imageFromPath(tempDir / "nope.png") }
        assertTrue(ex.message!!.contains("does not exist"), ex.message!!)
    }

    @Test
    fun `a directory is rejected`() {
        val dir = (tempDir / "subdir").also { it.createDirectory() }
        assertFailsWith<OllamaException> { imageFromPath(dir) }
    }

    @Test
    fun `image coerces an existing path`() {
        val file = (tempDir / "coerce.png").also { it.writeBytes(pngBytes) }
        assertEquals(pngBase64, image(file.toString()))
    }

    @Test
    fun `image coerces bare base64`() {
        assertEquals(pngBase64, image(pngBase64))
    }

    @Test
    fun `image coerces a data uri`() {
        assertEquals(pngBase64, image("data:image/png;base64,$pngBase64"))
    }

    @Test
    fun `image reports a missing file rather than treating the name as base64`() {
        val ex = assertFailsWith<OllamaException> { image("/tmp/definitely-not-here.png") }
        assertTrue(ex.message!!.contains("does not exist"), ex.message!!)
    }
}
