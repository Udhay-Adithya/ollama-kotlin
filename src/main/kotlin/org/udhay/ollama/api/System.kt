package org.udhay.ollama.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProcessResponse(
    val models: List<JsonElement> = emptyList(),
)

@Serializable
data class VersionResponse(
    val version: String,
)
