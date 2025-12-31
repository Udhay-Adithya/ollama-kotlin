package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GenerateRequest(
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

@Serializable
data class GenerateResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val response: String? = null,
    val done: Boolean? = null,
    @SerialName("done_reason")
    val doneReason: String? = null,
    val context: List<Int>? = null,
    val error: String? = null,
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

