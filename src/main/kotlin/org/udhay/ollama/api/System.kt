package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from `GET /api/ps` listing models currently loaded in memory.
 *
 * @property models The list of currently running (loaded) models.
 */
@Serializable
public data class ProcessResponse(
    val models: List<ProcessModel> = emptyList(),
)

/**
 * A model that is currently loaded in memory and ready for inference.
 *
 * @property name Full tag name of the running model.
 * @property model Base model identifier.
 * @property size Total model size in bytes.
 * @property sizeVram Amount of VRAM used by the model in bytes.
 * @property digest Content-addressable digest.
 * @property expiresAt ISO-8601 timestamp when this model will be unloaded.
 * @property details Architecture details of the model.
 * @property contextLength Maximum context length configured for this model.
 */
@Serializable
public data class ProcessModel(
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
)

/**
 * Response from `GET /api/version`.
 *
 * @property version The Ollama server version string.
 */
@Serializable
public data class VersionResponse(
    val version: String,
)
