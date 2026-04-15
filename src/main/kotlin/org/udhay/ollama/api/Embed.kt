package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request body for `POST /api/embed` — generate vector embeddings.
 *
 * @property model Name of the embedding model (e.g. `"nomic-embed-text"`).
 * @property input Text to embed. Can be a [JsonPrimitive] string or a [JsonArray] of strings.
 * @property truncate Whether to truncate input that exceeds the model's context length.
 * @property options Runtime options as a JSON object.
 * @property keepAlive How long to keep the model loaded (e.g. `"5m"`, `300`).
 * @property dimensions Desired dimensionality of the output embeddings (if supported by the model).
 */
@Serializable
public data class EmbedRequest(
    val model: String,
    val input: JsonElement,
    val truncate: Boolean? = null,
    val options: JsonElement? = null,
    @SerialName("keep_alive")
    val keepAlive: JsonElement? = null,
    val dimensions: Int? = null,
)

/**
 * Response from `POST /api/embed`.
 *
 * @property model Name of the model used.
 * @property embeddings List of embedding vectors. Each inner list is a float vector.
 * @property error Error message, if the request failed.
 */
@Serializable
public data class EmbedResponse(
    val model: String? = null,
    val embeddings: List<List<Double>>? = null,
    val error: String? = null,
)

