package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request body for `POST /api/chat` — multi-turn conversation with a model.
 *
 * @property model Name of the model to chat with (e.g. `"llama3"`).
 * @property messages Conversation history as a list of [Message] objects.
 * @property tools Tools the model may call during this turn.
 * @property format Constrain the output format. Pass a JSON Schema object for structured output.
 * @property stream `true` to receive tokens as they are generated via NDJSON streaming.
 * @property think Enable extended thinking. Pass `true`, or a level (`"high"`, `"medium"`, `"low"`).
 * @property logprobs Whether to return log probabilities for each generated token.
 * @property topLogprobs Number of top alternative tokens to include when [logprobs] is `true`.
 * @property options Runtime options (temperature, top_k, etc.) as a JSON object.
 * @property keepAlive How long to keep the model loaded (e.g. `"5m"`, `300`).
 */
@Serializable
public data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<Tool>? = null,
    val format: JsonElement? = null,
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
 * Response from `POST /api/chat`.
 *
 * In streaming mode each NDJSON line is deserialized into a [ChatResponse];
 * [done] is `true` only on the final chunk.
 *
 * @property model Name of the model that generated the response.
 * @property createdAt ISO-8601 timestamp of when the response was created.
 * @property message The assistant's message for this chunk.
 * @property done `true` when the response is complete.
 * @property doneReason Reason the model stopped generating (e.g. `"stop"`, `"length"`).
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
public data class ChatResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: Message? = null,
    val done: Boolean? = null,
    @SerialName("done_reason")
    val doneReason: String? = null,
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

/**
 * Log probability of a single token alternative.
 *
 * @property token The token text.
 * @property logprob Natural logarithm of the probability.
 */
@Serializable
public data class TokenLogprob(
    val token: String,
    val logprob: Double,
)

/**
 * Log probability information for a generated token, including top alternatives.
 *
 * @property token The generated token text.
 * @property logprob Natural logarithm of the probability.
 * @property topLogprobs The most likely alternative tokens and their log probabilities.
 */
@Serializable
public data class Logprob(
    val token: String,
    val logprob: Double,
    @SerialName("top_logprobs")
    val topLogprobs: List<TokenLogprob>? = null,
)
