package org.udhay.ollama.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Runtime model options (temperature, top_k, num_ctx, etc.).
 *
 * Modeled as a free-form [JsonElement] to stay forward-compatible with new options
 * added by the Ollama server without requiring a library update.
 *
 * ```kotlin
 * import kotlinx.serialization.json.buildJsonObject
 * import kotlinx.serialization.json.put
 *
 * val opts = Options(buildJsonObject {
 *     put("temperature", 0.7)
 *     put("top_k", 40)
 * })
 * ```
 *
 * @property json The underlying JSON object.
 */
@Serializable
@JvmInline
public value class Options(public val json: JsonElement)

