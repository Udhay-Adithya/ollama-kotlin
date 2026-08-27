# ollama-kotlin

The Ollama Kotlin library provides the easiest way to integrate Kotlin projects with [Ollama](https://github.com/ollama/ollama).

[![Maven Central](https://img.shields.io/maven-central/v/io.github.udhay-adithya/ollama-kotlin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.udhay-adithya/ollama-kotlin)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

## Prerequisites

- [Ollama](https://ollama.com/download) should be installed and running
- Pull a model to use with the library: `ollama pull <model>` e.g. `ollama pull gemma3`
  - See [Ollama.com](https://ollama.com/search) for more information on the models available.

## Installation

The library is available on Maven Central.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.udhay-adithya:ollama-kotlin:0.1.3")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.udhay-adithya:ollama-kotlin:0.1.3'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.udhay-adithya</groupId>
    <artifactId>ollama-kotlin</artifactId>
    <version>0.1.3</version>
</dependency>
```

### Version Catalog (libs.versions.toml)

```toml
[versions]
ollama = "0.1.3"

[libraries]
ollama-kotlin = { module = "io.github.udhay-adithya:ollama-kotlin", version.ref = "ollama" }
```
Then in your `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.ollama.kotlin)
}
```

## Quick Start

```kotlin
import org.udhay.ollama.OllamaClient
import org.udhay.ollama.api.*

// Create a client (defaults to http://127.0.0.1:11434)
val client = OllamaClient()

// One-shot chat
val response = client.chat(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = MessageRole.User, content = "Why is the sky blue?")
        )
    )
)
println(response.message?.content)

// Don't forget to close when done
client.close()
```

Or use Kotlin's `use` block for automatic resource management:

```kotlin
OllamaClient().use { client ->
    val response = client.generate(
        GenerateRequest(model = "llama3", prompt = "Hello!")
    )
    println(response.response)
}
```

## Configuration

### DSL Builder

```kotlin
val client = OllamaClient {
    host = "http://192.168.1.100:11434"
    headers["X-Custom-Header"] = "value"
}
```

### Timeouts

| Option | Default | Meaning |
|---|---|---|
| `requestTimeoutMillis` | `null` | Ceiling on the whole exchange. `null` means none. |
| `connectTimeoutMillis` | `30_000` | Time allowed to establish the connection. |
| `socketTimeoutMillis` | `null` | Maximum inactivity between bytes. `null` means none. |

`requestTimeoutMillis` bounds the response body read as well, so **any finite value also caps
streaming** — a long `chatStream()` or a `pullStream()` of a large model would be cut off
mid-flight once it elapsed. It defaults to `null` for that reason, matching `ollama-python`.

To detect a stalled connection without capping total duration, use `socketTimeoutMillis`, which
measures inactivity rather than elapsed time. Leave generous headroom: a cold model load can pause
for minutes before the first token arrives.

```kotlin
val client = OllamaClient {
    host = "http://192.168.1.100:11434"
    socketTimeoutMillis = 600_000   // give up after 10 minutes of silence
}
```

### Data Class

```kotlin
val client = OllamaClient(
    OllamaClientConfig(
        host = "http://192.168.1.100:11434",
        headers = mapOf("X-Custom-Header" to "value"),
    )
)
```

### Dynamic Configuration

Provide a `suspend` lambda to resolve the configuration for every request:

```kotlin
val client = OllamaClient(
    configProvider = {
        val settings = userSettingsRepository.get()
        OllamaClientConfig(
            host = settings.baseUrl,
            headers = mapOf("Authorization" to "Bearer ${settings.token}")
        )
    }
)
```

### Environment Variables

| Variable | Description |
|---|---|
| `OLLAMA_HOST` | Base URL of the Ollama server (default: `http://127.0.0.1:11434`) |
| `OLLAMA_API_KEY` | API key — automatically sent as a `Bearer` token in the `Authorization` header |

Environment variables are used as fallbacks when no explicit configuration is provided.

---

## API Reference

### Chat — `chat()` / `chatStream()`

Multi-turn conversation with a model.

```kotlin
// One-shot (suspending, waits for full response)
val response = client.chat(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = MessageRole.System, content = "You are a helpful assistant."),
            Message(role = MessageRole.User, content = "What is Kotlin?")
        )
    )
)
println(response.message?.content)
println("Tokens generated: ${response.evalCount}")
```

```kotlin
// Streaming (returns a Flow)
client.chatStream(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = MessageRole.User, content = "Tell me a story")
        )
    )
).collect { chunk ->
    print(chunk.message?.content ?: "")
}
```

### Generate — `generate()` / `generateStream()`

Single-turn text completion.

```kotlin
// One-shot
val response = client.generate(
    GenerateRequest(model = "llama3", prompt = "Explain quantum computing in one sentence.")
)
println(response.response)
```

```kotlin
// Streaming
client.generateStream(
    GenerateRequest(model = "llama3", prompt = "Write a haiku about Kotlin")
).collect { chunk ->
    print(chunk.response ?: "")
}
```

#### Fill-in-the-Middle

```kotlin
val response = client.generate(
    GenerateRequest(
        model = "codellama",
        prompt = "fun fibonacci(n: Int): Int {",
        suffix = "}\n\nfun main() { println(fibonacci(10)) }"
    )
)
```

### Embed — `embed()`

Generate vector embeddings.

```kotlin
import kotlinx.serialization.json.JsonPrimitive

val response = client.embed(
    EmbedRequest(
        model = "nomic-embed-text",
        input = JsonPrimitive("Kotlin is a modern programming language")
    )
)
println("Embedding dimensions: ${response.embeddings?.firstOrNull()?.size}")
```

For multiple inputs, pass a `JsonArray`:

```kotlin
import kotlinx.serialization.json.JsonArray

val response = client.embed(
    EmbedRequest(
        model = "nomic-embed-text",
        input = JsonArray(listOf(
            JsonPrimitive("First text"),
            JsonPrimitive("Second text")
        ))
    )
)
```

### Images (Multimodal)

`images` is a list of bare base64 strings on the wire. The helpers in `org.udhay.ollama.util`
encode from whatever you have:

```kotlin
import org.udhay.ollama.util.imageFromPath
import java.nio.file.Path

val response = client.chat(
    ChatRequest(
        model = "llava",
        messages = listOf(
            Message(
                role = MessageRole.User,
                content = "What is in this image?",
                images = listOf(imageFromPath(Path.of("photo.png")))
            )
        )
    )
)
```

| Helper | Input |
|---|---|
| `imageFromPath(path)` | A file on disk. Throws if it is missing. |
| `imageFromBytes(bytes)` | Already-loaded `ByteArray`. |
| `imageFromStream(input)` | An `InputStream`, read fully but not closed. |
| `imageFromBase64(value)` | Base64, stripping a `data:image/png;base64,` prefix if present. |
| `image(value)` | Either a path or base64 — coerces, for user-supplied values. |

Passing a `data:` URI through unchanged yields an image the model cannot decode, so route
browser-sourced strings through `imageFromBase64`.

### Tool Calling

Define tools and let the model invoke them:

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val addTool = Tool(
    function = ToolFunction(
        name = "add",
        description = "Adds two numbers",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("a", buildJsonObject { put("type", "number") })
                put("b", buildJsonObject { put("type", "number") })
            })
            put("required", kotlinx.serialization.json.JsonArray(listOf(
                JsonPrimitive("a"), JsonPrimitive("b")
            )))
        }
    )
)

val response = client.chat(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = MessageRole.User, content = "What is 2 + 3?")
        ),
        tools = listOf(addTool)
    )
)

// Use convenience extensions
import org.udhay.ollama.api.toolCalls
import org.udhay.ollama.api.functionName
import org.udhay.ollama.api.argumentsObject

for (call in response.toolCalls) {
    println("Function: ${call.functionName()}")
    println("Arguments: ${call.argumentsObject()}")
}
```

### Structured Output

Constrain the model's output to a JSON Schema:

```kotlin
val response = client.chat(
    ChatRequest(
        model = "llama3",
        messages = listOf(
            Message(role = MessageRole.User, content = "List 3 colors")
        ),
        format = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("colors", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
            })
        }
    )
)
```

### Extended Thinking

Enable chain-of-thought reasoning:

```kotlin
val response = client.chat(
    ChatRequest(
        model = "qwen3",
        messages = listOf(
            Message(role = MessageRole.User, content = "Solve: 15 * 23 + 47")
        ),
        think = JsonPrimitive(true) // or JsonPrimitive("low"), "medium", "high", "max"
    )
)
// /api/chat returns reasoning on the message; /api/generate returns it at the top level
println("Thinking: ${response.message?.thinking}")
println("Answer: ${response.message?.content}")
```

### Model Management

#### List Models

```kotlin
val models = client.list()
for (model in models.models) {
    println("${model.name} (${model.digest})")
}
```

#### Show Model Info

```kotlin
val info = client.show(ShowRequest(model = "llama3"))
println("Family: ${info.details?.family}")
println("Parameters: ${info.details?.parameterSize}")
println("Quantization: ${info.details?.quantizationLevel}")
```

#### Copy a Model

```kotlin
client.copy(CopyRequest(source = "llama3", destination = "my-llama3"))
```

#### Delete a Model

```kotlin
client.delete(DeleteRequest(model = "my-llama3"))
```

#### Create a Model

```kotlin
// One-shot
val result = client.create(
    CreateRequest(
        model = "my-custom-model",
        fromModel = "llama3",
        system = "You always respond in pirate speak."
    )
)

// Streaming progress
client.createStream(
    CreateRequest(model = "my-model", fromModel = "llama3")
).collect { progress ->
    println("${progress.status}: ${progress.completed ?: 0}/${progress.total ?: 0}")
}
```

#### Create from a local GGUF

`/api/create` needs either `from` or `files`. To build from local weights, upload the file first
and pass the digest `createBlob` returns:

```kotlin
import java.nio.file.Path

val digest = client.createBlob(Path.of("/models/my-model.gguf"))

client.create(
    CreateRequest(
        model = "my-model",
        files = mapOf("my-model.gguf" to digest)
    )
)
```

Sharded models list every shard, and LoRA adapters use `adapters` in the same digest-map form:

```kotlin
client.create(
    CreateRequest(
        model = "tuned",
        files = mapOf("base.gguf" to baseDigest),
        adapters = mapOf("lora.gguf" to loraDigest)
    )
)
```

### Pull / Push

#### Pull a Model

```kotlin
// One-shot (waits for download to complete)
val result = client.pull(PullRequest(model = "llama3"))

// Streaming progress
client.pullStream(PullRequest(model = "llama3")).collect { progress ->
    println("${progress.status}: ${progress.completed ?: 0}/${progress.total ?: 0}")
}
```

#### Push a Model

```kotlin
client.pushStream(PushRequest(model = "my-model")).collect { progress ->
    println(progress.status)
}
```

### Blob Upload

Upload model blobs by file path:

```kotlin
import java.nio.file.Path

// Auto-computes SHA-256 digest
val digest = client.createBlob(Path.of("/path/to/model-file"))

// Or provide a known digest
client.createBlob("sha256:abc123...", Path.of("/path/to/model-file"))
```

### System

#### List Running Models

```kotlin
val running = client.ps()
for (model in running.models) {
    println("${model.name} — VRAM: ${model.sizeVram} bytes, Context: ${model.contextLength}")
}
```

#### Server Version

```kotlin
val version = client.version()
println("Ollama ${version.version}")
```

### Ping (Health Check)

```kotlin
if (client.ping()) {
    println("Ollama is running")
}
```

### Web Search / Fetch

These two endpoints are served by Ollama's cloud, not by your local server, so they ignore
`host` and use `webHost` (default `https://ollama.com`). They require an `OLLAMA_API_KEY`, or an
explicit `Authorization: Bearer` header — without one the call throws `OllamaException` rather than
returning a 401.

```kotlin
val results = client.webSearch(
    WebSearchRequest(query = "Kotlin multiplatform", maxResults = 5)
)
for (result in results.results) {
    println("${result.title}: ${result.url}")
}

val page = client.webFetch(
    WebFetchRequest(url = "https://kotlinlang.org")
)
println(page.content)
```

To route these through a proxy or an enterprise gateway, override `webHost`:

```kotlin
val client = OllamaClient {
    host = "http://192.168.1.100:11434"   // local inference
    webHost = "https://proxy.internal"    // hosted web API
}
```

---

## Streaming with Flow

All streaming methods return a `kotlinx.coroutines.flow.Flow`. This integrates naturally with:

- **Kotlin coroutines** — `collect`, `toList`, `first`, etc.
- **Jetpack Compose** — `collectAsState()` for reactive UI updates
- **Ktor WebSockets** — pipe tokens to clients in real-time

```kotlin
// Jetpack Compose example
@Composable
fun ChatScreen(client: OllamaClient) {
    var text by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        client.chatStream(
            ChatRequest(
                model = "llama3",
                messages = listOf(Message(role = MessageRole.User, content = "Hello!"))
            )
        ).collect { chunk ->
            text += chunk.message?.content.orEmpty()
        }
    }
    
    Text(text)
}
```

## Error Handling

All API errors throw `OllamaException` with context:

```kotlin
try {
    client.chat(ChatRequest(model = "nonexistent", messages = emptyList()))
} catch (e: OllamaException) {
    println("HTTP ${e.statusCode}: ${e.message}")
    println("Response body: ${e.responseBody}")
}
```

Streaming errors (model errors mid-stream) are also thrown as `OllamaException`:

```kotlin
try {
    client.generateStream(request).collect { chunk ->
        print(chunk.response)
    }
} catch (e: OllamaException) {
    println("Stream error: ${e.message}")
}
```

## Runtime Options

`Options` carries the documented model options as typed fields. Only the ones you set are sent:

```kotlin
import org.udhay.ollama.api.Options

val response = client.generate(
    GenerateRequest(
        model = "llama3",
        prompt = "Be creative!",
        options = Options(
            temperature = 1.2,
            topK = 50,
            topP = 0.9,
            numCtx = 4096
        )
    )
)
```

Kotlin names are camelCase; they serialize to the snake_case the API expects (`numCtx` →
`num_ctx`). Load-time options (`numGpu`, `useMlock`, `numThread`, ...) and sampling options
(`seed`, `numPredict`, `repeatPenalty`, `mirostat`, `stop`, ...) are all present.

For an option newer than this library, use `extra` — it is merged into the same JSON object rather
than nested:

```kotlin
import kotlinx.serialization.json.JsonPrimitive

options = Options(
    temperature = 0.2,
    extra = mapOf("some_new_option" to JsonPrimitive(true))
)
```

---

## Generating API Documentation

This project uses [Dokka](https://github.com/Kotlin/dokka) for generating KDoc-based API documentation as HTML.

```bash
./gradlew dokkaGenerate
```

The generated HTML documentation will be in `build/dokka/html/`. Open `index.html` in a browser.

## Building

```bash
./gradlew build
```

## Running Tests

```bash
./gradlew test
```

## Requirements

- **Kotlin** 2.0+
- **JDK** 21+
- **Ollama** server running locally or accessible over the network

## License

[MIT License](LICENSE)
