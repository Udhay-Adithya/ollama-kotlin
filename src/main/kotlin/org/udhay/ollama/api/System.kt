package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProcessResponse(
    val models: List<ProcessModel> = emptyList(),
)

@Serializable
data class ProcessModel(
    val name: String? = null,
    val model: String? = null,
    val size: Long? = null,
    @SerialName("size_vram")
    val sizeVram: Long? = null,
    @SerialName("digest")
    val digest: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    val details: ModelDetails? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    /** Raw fallback for any additional fields returned by newer servers. */
    val raw: JsonElement? = null,
)

@Serializable
data class VersionResponse(
    val version: String,
)
