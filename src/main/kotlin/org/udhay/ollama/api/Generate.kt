package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request body for `POST /api/generate` — single-turn text completion.
 *
 * @property model Name of the model (e.g. `"llama3"`).
 * @property prompt The prompt to generate a completion for.
 * @property suffix Text that comes after the generated completion (fill-in-the-middle).
 * @property system Override the model's default system prompt.
 * @property template Override the model's prompt template.
 * @property context Conversation context from a previous [GenerateResponse] (for multi-turn without chat).
 * @property raw If `true`, bypass the prompt template and pass [prompt] directly to the model.
 * @property format Constrain the output format. Pass a JSON Schema object for structured output.
 * @property images Base64-encoded images for multimodal models.
 * @property stream `true` to receive tokens as they are generated via NDJSON streaming.
 *   Ignored by [org.udhay.ollama.OllamaClient.generate], which always sends `false`; use
 *   [org.udhay.ollama.OllamaClient.generateStream] to stream.
 * @property think Enable extended thinking. Pass `true`, or a level (`"high"`, `"medium"`, `"low"`).
 * @property logprobs Whether to return log probabilities for each generated token.
 * @property topLogprobs Number of top alternative tokens to include when [logprobs] is `true`.
 * @property options Runtime options (temperature, top_k, etc.) as a JSON object.
 * @property keepAlive How long to keep the model loaded (e.g. `"5m"`, `300`).
 */
@Serializable
public data class GenerateRequest(
    val model: String,
    val prompt: String,
    val suffix: String? = null,
    val system: String? = null,
    val template: String? = null,
    val context: List<Int>? = null,
    val raw: Boolean? = null,
    val format: JsonElement? = null,
    val images: List<String>? = null,
    val stream: Boolean? = null,
    val think: JsonElement? = null,
    val logprobs: Boolean? = null,
    @SerialName("top_logprobs")
    val topLogprobs: Int? = null,
    val options: JsonElement? = null,
    @SerialName("keep_alive")
    val keepAlive: JsonElement? = null,
)

/**
 * Response from `POST /api/generate`.
 *
 * In streaming mode each NDJSON line is a [GenerateResponse]; [done] is `true` on the final chunk.
 *
 * @property model Name of the model that generated the response.
 * @property createdAt ISO-8601 timestamp of when the response was created.
 * @property response The generated text (or partial text in streaming mode).
 * @property done `true` when generation is complete.
 * @property doneReason Reason the model stopped (e.g. `"stop"`, `"length"`).
 * @property context Conversation context that can be passed back for multi-turn continuation.
 * @property error Error message, if the request failed.
 * @property thinking The model's chain-of-thought reasoning, if extended thinking was enabled.
 * @property logprobs Per-token log probabilities, when requested.
 * @property totalDuration Total time spent in nanoseconds.
 * @property loadDuration Time spent loading the model in nanoseconds.
 * @property promptEvalCount Number of tokens in the prompt.
 * @property promptEvalDuration Time spent evaluating the prompt in nanoseconds.
 * @property evalCount Number of tokens generated.
 * @property evalDuration Time spent generating tokens in nanoseconds.
 */
@Serializable
public data class GenerateResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val response: String? = null,
    val done: Boolean? = null,
    @SerialName("done_reason")
    val doneReason: String? = null,
    val context: List<Int>? = null,
    val error: String? = null,
    val thinking: String? = null,
    val logprobs: List<Logprob>? = null,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null,
)
