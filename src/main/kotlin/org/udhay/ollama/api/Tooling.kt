package org.udhay.ollama.api

import kotlinx.serialization.json.JsonObject

/**
 * Convenience helpers for tool calling.
 */
val ChatResponse.toolCalls: List<ToolCall>
    get() = message?.toolCalls.orEmpty()

val Message.hasToolCalls: Boolean
    get() = !toolCalls.isNullOrEmpty()

fun ToolCall.functionName(): String? = function?.name

fun ToolCall.argumentsObject(): JsonObject? = function?.arguments as? JsonObject

