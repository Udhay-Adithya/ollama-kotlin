package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class MessageRole {
    @SerialName("system")
    System,

    @SerialName("user")
    User,

    @SerialName("assistant")
    Assistant,

    @SerialName("tool")
    Tool,
}

@Serializable
data class Message(
    val role: MessageRole,
    val content: String? = null,
    val images: List<String>? = null,
    @SerialName("tool_name")
    val toolName: String? = null,
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null,
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: ToolFunction,
)

