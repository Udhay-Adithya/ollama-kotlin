package org.udhay.ollama

/**
 * Convenience factory for creating an [OllamaClient] with the given [config].
 */
public fun OllamaClient(config: OllamaClientConfig = OllamaClientConfig()): OllamaClient =
    OllamaClient(config, engine = null)

/**
 * Convenience factory for creating an [OllamaClient] with a dynamic [configProvider].
 */
public fun OllamaClient(configProvider: suspend () -> OllamaClientConfig): OllamaClient =
    OllamaClient(configProvider, engine = null)

