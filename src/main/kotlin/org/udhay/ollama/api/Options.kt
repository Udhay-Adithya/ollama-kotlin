package org.udhay.ollama.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Runtime options are intentionally modeled as a free-form JSON object.
 */
@Serializable
@JvmInline
value class Options(val json: JsonElement)

