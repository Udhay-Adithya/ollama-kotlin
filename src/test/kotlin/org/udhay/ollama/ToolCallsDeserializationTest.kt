package org.udhay.ollama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.udhay.ollama.api.ChatResponse
import org.udhay.ollama.internal.DefaultJson

class ToolCallsDeserializationTest {
    @Test
    fun `chat response tool_calls can be deserialized`() {
        val json = """
            {
              "model": "m",
              "created_at": "2025-01-01T00:00:00Z",
              "message": {
                "role": "assistant",
                "content": "",
                "tool_calls": [
                  {
                    "id": "call_1",
                    "type": "function",
                    "function": {
                      "name": "add",
                      "arguments": {"a": 1, "b": 2}
                    }
                  }
                ]
              },
              "done": false
            }
        """.trimIndent()

        val response = DefaultJson.decodeFromString<ChatResponse>(json)
        val toolCalls = response.message?.toolCalls
        assertNotNull(toolCalls)
        assertEquals(1, toolCalls.size)
        assertEquals("call_1", toolCalls[0].id)
        assertEquals("add", toolCalls[0].function?.name)
    }
}
