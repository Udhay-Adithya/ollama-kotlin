package org.udhay.ollama

import kotlin.test.Test
import kotlin.test.assertNotNull

class OllamaClientSmokeTest {
    @Test
    fun `client can be constructed`() {
        val client = OllamaClient()
        assertNotNull(client)
    }
}

