package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EmbedRequest(
    val model: String,
    val input: JsonElement,
    val truncate: Boolean? = null,
    val options: JsonElement? = null,
    @SerialName("keep_alive")
    val keepAlive: JsonElement? = null,
    val dimensions: Int? = null,
)

@Serializable
data class EmbedResponse(
    val model: String? = null,
    val embeddings: List<List<Double>>? = null,
    val error: String? = null,
)

