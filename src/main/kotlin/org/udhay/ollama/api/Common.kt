package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Role of a participant in a conversation.
 */
@Serializable
public enum class MessageRole {
    /** System prompt that sets the model's behavior. */
    @SerialName("system")
    System,

    /** A message from the user. */
    @SerialName("user")
    User,

    /** A response from the model. */
    @SerialName("assistant")
    Assistant,

    /** A tool/function result returned to the model. */
    @SerialName("tool")
    Tool,
}

/**
 * The function portion of a [ToolCall], containing the function name and its arguments.
 *
 * @property name Name of the function the model wants to call.
 * @property arguments Function arguments as a JSON object.
 */
@Serializable
public data class ToolCallFunction(
    val name: String? = null,
    val arguments: JsonElement? = null,
)

/**
 * A tool call request emitted by the model during a chat turn.
 *
 * @property id Unique identifier for this tool call.
 * @property type Type of tool call (typically `"function"`).
 * @property function The function name and arguments.
 */
@Serializable
public data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunction? = null,
)

/**
 * A single message in a conversation.
 *
 * @property role The role of the message author.
 * @property content Text content of the message.
 * @property images Base64-encoded images attached to this message (for multimodal models).
 * @property toolName Name of the tool that produced this message (when [role] is [MessageRole.Tool]).
 * @property toolCalls Tool call requests produced by the model (when [role] is [MessageRole.Assistant]).
 * @property toolCallId ID of the tool call this message is responding to.
 */
@Serializable
public data class Message(
    val role: MessageRole,
    val content: String? = null,
    val images: List<String>? = null,

    @SerialName("tool_name")
    val toolName: String? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,

    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)

/**
 * Describes a function that a model can call.
 *
 * @property name Function name.
 * @property description Human-readable description of what the function does.
 * @property parameters JSON Schema describing the function's parameters.
 */
@Serializable
public data class ToolFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null,
)

/**
 * Definition of a tool available to the model.
 *
 * @property type Tool type (defaults to `"function"`).
 * @property function The function definition.
 */
@Serializable
public data class Tool(
    val type: String = "function",
    val function: ToolFunction,
)
