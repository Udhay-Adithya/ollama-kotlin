package org.udhay.ollama

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.Tool
import org.udhay.ollama.api.ToolFunction
import org.udhay.ollama.api.tool
import org.udhay.ollama.internal.DefaultJson
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `ollama-python` generates tool schemas from a function signature and docstring. Kotlin cannot
 * read parameter names or KDoc at runtime, so the equivalent is a builder — and it has to produce
 * exactly the schema a hand-written `Tool` would.
 */
class ToolBuilderTest {

    private fun Tool.params(): JsonObject = function.parameters!!.jsonObject

    @Test
    fun `the builder matches the hand-written schema it replaces`() {
        val handWritten = Tool(
            function = ToolFunction(
                name = "add",
                description = "Adds two numbers",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("a", buildJsonObject { put("type", "number") })
                            put("b", buildJsonObject { put("type", "number") })
                        },
                    )
                    put("required", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
                },
            ),
        )

        val built = tool("add", "Adds two numbers") {
            number("a")
            number("b")
        }

        assertEquals(
            DefaultJson.encodeToString(Tool.serializer(), handWritten),
            DefaultJson.encodeToString(Tool.serializer(), built),
        )
    }

    @Test
    fun `required tracks the parameters that were not opted out`() {
        val built = tool("f") {
            string("a")
            string("b", required = false)
            integer("c")
        }
        assertEquals(
            listOf("a", "c"),
            built.params().getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `descriptions reach the schema`() {
        val built = tool("weather", "Gets the weather") {
            string("city", "City name")
        }
        assertEquals("Gets the weather", built.function.description)
        assertEquals(
            "City name",
            built.params().getValue("properties").jsonObject.getValue("city").jsonObject
                .getValue("description").jsonPrimitive.content,
        )
    }

    @Test
    fun `an enum constrains a string parameter`() {
        val built = tool("f") {
            string("unit", enum = listOf("celsius", "fahrenheit"))
        }
        val unit = built.params().getValue("properties").jsonObject.getValue("unit").jsonObject
        assertEquals("string", unit.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf("celsius", "fahrenheit"),
            unit.getValue("enum").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `each scalar type emits its own JSON Schema type`() {
        val built = tool("f") {
            string("s")
            number("n")
            integer("i")
            boolean("b")
        }
        val props = built.params().getValue("properties").jsonObject
        assertEquals("string", props.getValue("s").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("number", props.getValue("n").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("integer", props.getValue("i").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("boolean", props.getValue("b").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `an array declares its item type`() {
        val built = tool("f") { array("tags", itemType = "string", description = "Labels") }
        val tags = built.params().getValue("properties").jsonObject.getValue("tags").jsonObject
        assertEquals("array", tags.getValue("type").jsonPrimitive.content)
        assertEquals("string", tags.getValue("items").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `a nested object carries its own properties and required list`() {
        val built = tool("f") {
            obj("location", "Where to look") {
                string("city")
                string("country", required = false)
            }
        }
        val location = built.params().getValue("properties").jsonObject.getValue("location").jsonObject
        assertEquals("object", location.getValue("type").jsonPrimitive.content)
        assertTrue(location.getValue("properties").jsonObject.containsKey("city"))
        assertEquals(
            listOf("city"),
            location.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `raw accepts a schema fragment the helpers do not cover`() {
        val built = tool("f") {
            raw("either", buildJsonObject { put("oneOf", JsonArray(listOf(buildJsonObject { put("type", "string") }))) })
        }
        assertTrue(
            built.params().getValue("properties").jsonObject.getValue("either").jsonObject
                .containsKey("oneOf"),
        )
    }

    @Test
    fun `a tool with no parameters still emits a valid empty schema`() {
        val built = tool("now", "Current time")
        assertEquals("object", built.params().getValue("type").jsonPrimitive.content)
        assertEquals(0, built.params().getValue("properties").jsonObject.size)
        assertEquals(0, built.params().getValue("required").jsonArray.size)
    }

    @Test
    fun `the tool type defaults to function`() {
        assertEquals("function", tool("f").type)
    }

    @Test
    fun `an omitted description is left out rather than sent as null`() {
        val encoded = DefaultJson.encodeToString(Tool.serializer(), tool("f"))
        assertNull(DefaultJson.parseToJsonElement(encoded).jsonObject["function"]!!.jsonObject["description"])
    }
}
