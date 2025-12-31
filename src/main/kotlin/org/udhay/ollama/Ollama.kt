package org.udhay.ollama

/**
 * Convenience factory.
 */
fun ollama(config: OllamaClientConfig = OllamaClientConfig()): OllamaClient = OllamaClient(config)

