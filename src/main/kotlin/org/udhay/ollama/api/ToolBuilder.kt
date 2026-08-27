package org.udhay.ollama.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a [Tool] with its JSON Schema generated from a parameter list.
 *
 * ```kotlin
 * val addTool = tool("add", "Adds two numbers") {
 *     number("a", "First addend")
 *     number("b", "Second addend")
 *     string("unit", "Optional unit", required = false)
 * }
 * ```
 *
 * `ollama-python` derives this from a function signature plus docstring. Kotlin cannot read
 * parameter names or KDoc at runtime, so a builder is the equivalent that stays type-safe.
 *
 * @param name Function name the model will call.
 * @param description What the function does. The model relies on this to decide when to call it.
 */
public fun tool(
    name: String,
    description: String? = null,
    block: ToolParametersBuilder.() -> Unit = {},
): Tool = Tool(
    function = ToolFunction(
        name = name,
        description = description,
        parameters = ToolParametersBuilder().apply(block).build(),
    ),
)

/**
 * Declares the parameters of a [tool], tracking which are required.
 *
 * Every parameter is required unless `required = false` is passed. This matches how tools are
 * usually written and removes the bookkeeping of maintaining a separate `required` array.
 */
public class ToolParametersBuilder {

    private val properties = mutableMapOf<String, JsonElement>()
    private val required = mutableListOf<String>()

    /** A string parameter. Pass [enum] to constrain it to a fixed set of values. */
    public fun string(
        name: String,
        description: String? = null,
        enum: List<String>? = null,
        required: Boolean = true,
    ): Unit = add(name, required) {
        put("type", "string")
        description?.let { put("description", it) }
        enum?.let { put("enum", JsonArray(it.map(::JsonPrimitive))) }
    }

    /** A floating-point parameter. */
    public fun number(
        name: String,
        description: String? = null,
        required: Boolean = true,
    ): Unit = add(name, required) {
        put("type", "number")
        description?.let { put("description", it) }
    }

    /** An integer parameter. */
    public fun integer(
        name: String,
        description: String? = null,
        required: Boolean = true,
    ): Unit = add(name, required) {
        put("type", "integer")
        description?.let { put("description", it) }
    }

    /** A boolean parameter. */
    public fun boolean(
        name: String,
        description: String? = null,
        required: Boolean = true,
    ): Unit = add(name, required) {
        put("type", "boolean")
        description?.let { put("description", it) }
    }

    /**
     * An array parameter.
     *
     * @param itemType JSON Schema type of the elements, e.g. `"string"` or `"number"`.
     */
    public fun array(
        name: String,
        itemType: String = "string",
        description: String? = null,
        required: Boolean = true,
    ): Unit = add(name, required) {
        put("type", "array")
        description?.let { put("description", it) }
        put("items", buildJsonObject { put("type", itemType) })
    }

    /** A nested object parameter, described by another parameter block. */
    public fun obj(
        name: String,
        description: String? = null,
        required: Boolean = true,
        block: ToolParametersBuilder.() -> Unit,
    ) {
        val nested = ToolParametersBuilder().apply(block).build()
        add(name, required) {
            put("type", "object")
            description?.let { put("description", it) }
            nested.forEach { (key, value) -> put(key, value) }
        }
    }

    /**
     * A parameter described by a raw JSON Schema fragment, for shapes the typed helpers do not
     * cover — `oneOf`, `$ref`, tuple-typed arrays and so on.
     */
    public fun raw(name: String, schema: JsonObject, required: Boolean = true) {
        properties[name] = schema
        if (required) this.required += name
    }

    private fun add(name: String, isRequired: Boolean, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        properties[name] = buildJsonObject(block)
        if (isRequired) required += name
    }

    internal fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        // Emitted even when empty so the schema is explicit about taking no required arguments.
        put("required", JsonArray(required.map(::JsonPrimitive)))
    }
}
