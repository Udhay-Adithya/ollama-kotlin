package org.udhay.ollama

import kotlin.test.Test
import kotlin.test.assertEquals
import org.udhay.ollama.util.sha256DigestOf

class Sha256DigestTest {
    @Test
    fun `sha256DigestOf returns sha256 prefix`() {
        val bytes = "abc".encodeToByteArray()
        val digest = sha256DigestOf(bytes.inputStream())

        // Known sha256("abc")
        assertEquals(
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest
        )
    }
}

