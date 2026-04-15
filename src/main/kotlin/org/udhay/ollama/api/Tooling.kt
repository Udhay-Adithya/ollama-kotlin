package org.udhay.ollama.api

import kotlinx.serialization.json.JsonObject

/**
 * Extension to extract all [ToolCall]s from a [ChatResponse].
 *
 * Returns an empty list if the assistant message contains no tool calls.
 */
public val ChatResponse.toolCalls: List<ToolCall>
    get() = message?.toolCalls.orEmpty()

/**
 * `true` if this [Message] contains one or more tool call requests.
 */
public val Message.hasToolCalls: Boolean
    get() = !toolCalls.isNullOrEmpty()

/**
 * Returns the name of the function this tool call invokes, or `null`.
 */
public fun ToolCall.functionName(): String? = function?.name

/**
 * Returns the function arguments as a [JsonObject], or `null` if absent or not an object.
 */
public fun ToolCall.argumentsObject(): JsonObject? = function?.arguments as? JsonObject

