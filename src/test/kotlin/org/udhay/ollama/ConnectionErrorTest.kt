package org.udhay.ollama

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.udhay.ollama.api.ChatRequest
import org.udhay.ollama.api.Message
import org.udhay.ollama.api.MessageRole
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A refused connection previously surfaced as a bare Ktor `ConnectException`, and
 * `UnresolvedAddressException` prints as a class name with no message at all.
 */
class ConnectionErrorTest {

    private fun failingClient(error: Throwable) = OllamaClient(
        OllamaClientConfig(host = "http://localhost:11434"),
        MockEngine { throw error },
    )

    @Test
    fun `a refused connection names ollama and how to fix it`() = runTest {
        val ex = assertFailsWith<OllamaException> {
            failingClient(ConnectException("Connection refused")).use {
                it.chat(ChatRequest(model = "m", messages = listOf(Message(role = MessageRole.User, content = "hi"))))
            }
        }
        assertTrue(ex.message!!.contains("Failed to connect to Ollama"), ex.message!!)
        assertTrue(ex.message!!.contains("ollama.com/download"), ex.message!!)
        assertIs<ConnectException>(ex.cause)
    }

    @Test
    fun `an unresolvable host points at the host configuration`() = runTest {
        val ex = assertFailsWith<OllamaException> {
            failingClient(UnknownHostException("nope.invalid")).use { it.version() }
        }
        assertTrue(ex.message!!.contains("Could not resolve"), ex.message!!)
        assertTrue(ex.message!!.contains("OLLAMA_HOST"), ex.message!!)
    }

    @Test
    fun `an unresolved address is mapped too`() = runTest {
        val ex = assertFailsWith<OllamaException> {
            failingClient(UnresolvedAddressException()).use { it.list() }
        }
        assertTrue(ex.message!!.contains("Could not resolve"), ex.message!!)
    }

    @Test
    fun `streaming calls get the same diagnostics`() = runTest {
        val ex = assertFailsWith<OllamaException> {
            failingClient(ConnectException("Connection refused")).use { c ->
                c.chatStream(
                    ChatRequest(model = "m", messages = listOf(Message(role = MessageRole.User, content = "hi"))),
                ).collect { }
            }
        }
        assertTrue(ex.message!!.contains("Failed to connect to Ollama"), ex.message!!)
    }

    @Test
    fun `unrelated failures are left untouched`() = runTest {
        val ex = assertFailsWith<IllegalStateException> {
            failingClient(IllegalStateException("something else")).use { it.version() }
        }
        assertEquals("something else", ex.message)
    }

    @Test
    fun `a non-2xx response still throws the http OllamaException, not a connection one`() = runTest {
        val engine = MockEngine { respond("nope", HttpStatusCode.NotFound) }
        val ex = assertFailsWith<OllamaException> {
            OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine).use { it.version() }
        }
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun `ping reports false for a refused connection`() = runTest {
        assertEquals(false, failingClient(ConnectException("Connection refused")).use { it.ping() })
    }

    @Test
    fun `ping propagates cancellation instead of reporting the server as down`() = runTest {
        val started = CompletableDeferred<Unit>()
        val engine = MockEngine {
            started.complete(Unit)
            // Never completes, so the only way out of the request is cancellation.
            CompletableDeferred<Nothing>().await()
        }
        val client = OllamaClient(OllamaClientConfig(host = "http://localhost:11434"), engine)

        // If ping() swallows CancellationException it returns normally and this is assigned.
        // If it rethrows, the coroutine unwinds and the assignment never happens.
        var returnedValue: Boolean? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            returnedValue = client.ping()
        }
        started.await()
        job.cancel()
        job.join()

        assertNull(
            returnedValue,
            "ping() swallowed the cancellation and returned $returnedValue instead of rethrowing",
        )
        client.close()
    }
}
