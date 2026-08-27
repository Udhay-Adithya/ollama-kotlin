package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from `GET /api/tags` containing all locally available models.
 *
 * @property models The list of model tags available on the server.
 */
@Serializable
public data class ListResponse(
    val models: List<ModelTag> = emptyList(),
)

/**
 * A single model entry returned in a [ListResponse].
 *
 * @property name Full tag name, e.g. `"llama3:latest"`.
 * @property model The base model identifier.
 * @property modifiedAt ISO-8601 timestamp of the last modification.
 * @property size Model size in bytes on disk.
 * @property digest Content-addressable digest (e.g. `"sha256:abc..."`).
 * @property details Opaque JSON details about the model architecture.
 */
@Serializable
public data class ModelTag(
    val name: String,
    val model: String? = null,
    @SerialName("modified_at")
    val modifiedAt: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: JsonElement? = null,
)

/**
 * Request body for `POST /api/show` to retrieve model metadata.
 *
 * @property model Name of the model to inspect.
 * @property system Override the system prompt stored in the Modelfile.
 * @property template Override the template stored in the Modelfile.
 * @property options Runtime options (temperature, top_k, etc.) as a JSON object.
 */
@Serializable
public data class ShowRequest(
    val model: String,
    val system: String? = null,
    val template: String? = null,
    val options: JsonElement? = null,
)

/**
 * Response from `POST /api/show` containing detailed model information.
 *
 * @property license License text of the model.
 * @property modelfile The contents of the Modelfile.
 * @property parameters Model parameters in string form.
 * @property template The prompt template.
 * @property system The system message.
 * @property details Structured metadata about the model architecture.
 * @property messages Default messages defined in the Modelfile.
 * @property modifiedAt ISO-8601 timestamp of the last modification.
 * @property modelInfo Raw model architecture information as JSON.
 * @property capabilities List of model capabilities (e.g. `"completion"`, `"tools"`).
 * @property projectorInfo Projector (vision adapter) information, if applicable.
 */
@Serializable
public data class ShowResponse(
    val license: String? = null,
    val modelfile: String? = null,
    val parameters: String? = null,
    val template: String? = null,
    val system: String? = null,
    val details: ModelDetails? = null,
    val messages: List<Message>? = null,
    @SerialName("modified_at")
    val modifiedAt: String? = null,
    @SerialName("model_info")
    val modelInfo: JsonElement? = null,
    val capabilities: List<String>? = null,
    @SerialName("projector_info")
    val projectorInfo: JsonElement? = null,
)

/**
 * Structured metadata about a model's architecture and quantization.
 *
 * @property parentModel Name of the parent model this was derived from.
 * @property format Model file format (e.g. `"gguf"`).
 * @property family Model family (e.g. `"llama"`, `"gemma"`).
 * @property families All model families this model belongs to.
 * @property parameterSize Human-readable parameter count (e.g. `"8B"`, `"70B"`).
 * @property quantizationLevel Quantization level (e.g. `"Q4_0"`, `"Q8_0"`).
 */
@Serializable
public data class ModelDetails(
    @SerialName("parent_model")
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    @SerialName("quantization_level")
    val quantizationLevel: String? = null,
)

/**
 * Request body for `POST /api/copy` to duplicate a model under a new name.
 *
 * @property source Name of the existing model to copy.
 * @property destination Name for the new copy.
 */
@Serializable
public data class CopyRequest(
    val source: String,
    val destination: String,
)

/**
 * Request body for `DELETE /api/delete` to remove a model.
 *
 * @property model Name of the model to delete.
 */
@Serializable
public data class DeleteRequest(
    val model: String,
)

/**
 * Request body for `POST /api/create` to create a new model.
 *
 * @property model Name for the new model.
 * @property fromModel Base model to derive from (maps to the `from` JSON field).
 * @property quantize Target quantization level (e.g. `"q4_0"`).
 * @property template Prompt template override.
 * @property license License text or array of license strings.
 * @property system System prompt override.
 * @property parameters Model parameters as JSON.
 * @property messages Default conversation messages.
 * @property adapters Adapter layers as JSON.
 * @property stream Whether to stream progress updates (`true`) or wait for completion.
 *   Ignored by the one-shot client methods, which always send `false`.
 */
@Serializable
public data class CreateRequest(
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

/**
 * Request body for `POST /api/pull` to download a model from a registry.
 *
 * @property model Name of the model to pull (e.g. `"llama3"`, `"llama3:70b"`).
 * @property insecure Allow pulling from insecure (HTTP) registries.
 * @property stream Whether to stream progress updates.
 *   Ignored by the one-shot client methods, which always send `false`.
 */
@Serializable
public data class PullRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null,
)

/**
 * Request body for `POST /api/push` to upload a model to a registry.
 *
 * @property model Name of the model to push.
 * @property insecure Allow pushing to insecure (HTTP) registries.
 * @property stream Whether to stream progress updates.
 *   Ignored by the one-shot client methods, which always send `false`.
 */
@Serializable
public data class PushRequest(
    val model: String,
    val insecure: Boolean? = null,
    val stream: Boolean? = null,
)

/**
 * Generic status response for operations like copy and delete.
 *
 * @property status Status message (e.g. `"success"`).
 * @property error Error message, if the operation failed.
 */
@Serializable
public data class StatusResponse(
    val status: String? = null,
    val error: String? = null,
)

/**
 * Progress update emitted during pull, push, and create operations.
 *
 * @property status Human-readable status (e.g. `"downloading"`, `"success"`).
 * @property digest Digest of the layer currently being processed.
 * @property total Total size in bytes of the current layer.
 * @property completed Bytes completed so far for the current layer.
 * @property error Error message, if any.
 */
@Serializable
public data class ProgressResponse(
    val status: String? = null,
    val digest: String? = null,
    val total: Long? = null,
    val completed: Long? = null,
    val error: String? = null,
)
