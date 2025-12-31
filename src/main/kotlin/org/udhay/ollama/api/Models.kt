package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ListResponse(
    val models: List<ModelTag> = emptyList(),
)

@Serializable
data class ModelTag(
    val name: String,
    val model: String? = null,
    val modified: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class ShowRequest(
    val model: String,
    val system: String? = null,
    val template: String? = null,
    val options: JsonElement? = null,
)

@Serializable
data class ShowResponse(
    val license: String? = null,
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    val system: String? = null,
    val details: JsonElement? = null,
    val model_info: JsonElement? = null,
)

@Serializable
data class CopyRequest(
    val source: String,
    val destination: String,
)

@Serializable
data class DeleteRequest(
    val model: String,
)

@Serializable
data class CreateRequest(
    val model: String,
    @SerialName("from")
    val fromModel: String? = null,
    val quantize: String? = null,
    val template: String? = null,
    val license: JsonElement? = null,
    val system: String? = null,
    val parameters: JsonElement? = null,
    val messages: List<Message>? = null,
    val adapters: JsonElement? = null,
    val stream: Boolean? = null,
)

@Serializable
data class PullRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null,
)

@Serializable
data class PushRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null,
)

@Serializable
data class StatusResponse(
    val status: String? = null,
    val error: String? = null,
)

@Serializable
data class ProgressResponse(
    val status: String? = null,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null,
    val error: String? = null,
)

