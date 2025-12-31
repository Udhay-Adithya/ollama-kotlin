package org.udhay.ollama.internal

internal object OllamaEnv {
    fun host(): String? = System.getenv("OLLAMA_HOST")?.takeIf { it.isNotBlank() }

    fun apiKey(): String? = System.getenv("OLLAMA_API_KEY")?.takeIf { it.isNotBlank() }
}

