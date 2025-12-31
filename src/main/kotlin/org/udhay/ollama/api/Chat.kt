package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRequest(
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

@Serializable
data class ChatResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: Message? = null,
    val done: Boolean? = null,
    @SerialName("done_reason")
    val doneReason: String? = null,
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

