# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `OllamaClientConfig.webHost` (default `https://ollama.com`) for routing the hosted web API
  through a proxy or gateway.

### Added
- An opt-in integration suite (`OLLAMA_INTEGRATION_TESTS=true`) exercising a real `ollama serve`,
  plus a scheduled `Integration` workflow that installs Ollama and runs it. The mock-based suite
  locks in whatever the client already believes about the wire protocol, so a wrong belief passes
  its own tests — which is how `chat()` shipped returning empty strings.
- Binary compatibility validation via the Kotlin `binary-compatibility-validator` plugin, with the
  public ABI committed at `api/ollama-kotlin.api`. `apiCheck` runs as part of `build`, so CI fails
  a PR that changes the public surface without an accompanying `./gradlew apiDump`.
- A default `User-Agent` of `ollama-kotlin/<version> (<os> <arch>) Kotlin/<version>`, matching the
  shape `ollama-python` sends. The version comes from a generated `BuildInfo` constant so it cannot
  drift from the published coordinates. Overridable via `OllamaClientConfig.headers`.
- `OllamaClientConfig.followRedirects` (default `true`). Ktor fixes redirect handling when the
  client is built rather than per request, so it is read from the config passed at construction;
  the `configProvider` constructor takes it as a parameter.
- `blobExists(digest)` wrapping `HEAD /api/blobs/:digest`. `createBlob(path)` now consults it first
  and skips the upload when the server already holds the blob, so re-running a create flow does not
  re-send gigabytes. Pass `skipIfPresent = false` to always upload.
- `embeddings(EmbeddingsRequest)` for the superseded `/api/embeddings` endpoint, for servers
  predating `/api/embed`. Deprecated in favour of `embed`, and note the shape difference — a single
  `embedding` vector rather than a list of `embeddings`.
- A `tool(name, description) { ... }` builder that generates a tool's JSON Schema from typed
  parameter declarations — `string`, `number`, `integer`, `boolean`, `array`, nested `obj`, and
  `raw` for schema fragments the helpers do not cover. Parameters are required unless opted out,
  so the `required` array is maintained automatically instead of by hand.
- `Options` now carries the full documented option set as typed fields — `temperature`, `topK`,
  `numCtx`, `seed`, `numPredict`, `repeatPenalty`, `mirostat`, `stop` and the rest — serialized to
  their snake_case wire names. `Options.extra` passes through options newer than this library,
  flattened into the same JSON object rather than nested.
- Image helpers in `org.udhay.ollama.util`: `imageFromPath`, `imageFromBytes`, `imageFromStream`,
  `imageFromBase64` and `image`. Callers previously had to base64-encode by hand, and a `data:` URI
  passed through verbatim produces an image the model cannot decode. `imageFromBase64` strips the
  prefix; a missing file is reported rather than silently sent as garbage.
- `CreateRequest.files` — a file-name to blob-digest map, so a model can be built from local GGUF
  weights. Previously `/api/create` could only derive from an existing model, and a request with
  neither is rejected by the server with `neither 'from' or 'files' was specified`.

### Changed
- `options` on `ChatRequest`, `GenerateRequest`, `EmbedRequest` and `ShowRequest` is now `Options?`
  instead of `JsonElement?`. `Options` was previously a value class that no request type accepted —
  unreachable public API. **Source-breaking** for callers passing `buildJsonObject { ... }`; the
  same values are now named fields, and anything unnamed goes in `Options.extra`.
- `CreateRequest.adapters` is now `Map<String, String>?` instead of `JsonElement?`, matching the
  digest-map shape `files` uses. This is a source-breaking change for anyone who was passing a raw
  `JsonElement`.
- `requestTimeoutMillis` now defaults to `null` (no ceiling) instead of 5 minutes. It bounds the
  response body read too, so any finite value also truncated streaming — a long `chatStream()` or a
  `pullStream()` of a large model died mid-flight once it elapsed. `ollama-python` likewise applies
  no request timeout. `connectTimeoutMillis` now defaults to 30 seconds so an unreachable host
  still fails fast, and `socketTimeoutMillis` is the recommended knob for catching a stalled
  stream since it measures inactivity rather than total duration.

### Fixed
- `ping()` caught every `Exception`, and `CancellationException` is one — so cancelling a scope
  during a ping reported the server as down instead of propagating, breaking structured
  concurrency. Cancellation now propagates; genuine failures still return `false`.
- Connection failures surfaced as bare `ConnectException` or, worse, `UnresolvedAddressException`
  with no message at all. Every endpoint — streaming included — now reports an `OllamaException`
  naming the URL and what to check.
- `createBlob()` read the entire file into a `ByteArray` before sending. Blobs are model weights and
  routinely run to several gigabytes, so this reliably exhausted the default heap. The file is now
  streamed from disk with a real `Content-Length`, keeping memory flat regardless of size. A missing
  file or a directory now fails with a clear `OllamaException` instead of a raw IO error.
- `webSearch()` and `webFetch()` were sent to the configured local host, where they 404. They are
  hosted by Ollama's cloud and now go to `webHost`, independent of `host`. Both also fail fast with
  a clear `OllamaException` when no `Authorization: Bearer` token is configured, rather than
  surfacing an opaque 401.
- `Message` was missing `thinking`, so reasoning returned by `/api/chat` was silently discarded by
  `ignoreUnknownKeys`. `ChatResponse.thinking` is the `/api/generate` shape and is always `null` for
  chat. Read chat reasoning from `response.message?.thinking`.
- Hosts without a scheme produced invalid request URLs. `OLLAMA_HOST=127.0.0.1:11434` — the form
  Ollama documents — became `localhost://localhost/11434/api/chat` or threw. Hosts are now
  normalized: a missing scheme defaults to `http`, a missing port to `11434`, a bare `:port` binds
  to `127.0.0.1`, IPv6 literals keep their brackets, and path prefixes are preserved. Unlike
  `ollama-python`, an explicit scheme without a port keeps that port implicit rather than gaining
  `:80`/`:443`, which some gateways reject.
- `chat()` and `generate()` returned empty content. `/api/chat` and `/api/generate` stream by
  default, so omitting `stream` made the server reply with NDJSON and the client returned only the
  final chunk — which carries empty content. The one-shot methods now send `"stream": false`
  explicitly. `create()`, `pull()` and `push()` send it too, for consistency.

## [0.1.3] - 2026-05-24

### Added
- **Configurable timeouts** via `OllamaClientConfig` (`requestTimeoutMillis`, `connectTimeoutMillis`, `socketTimeoutMillis`).
- Default `requestTimeoutMillis` set to **5 minutes** (300,000 ms) to better support LLM workloads.
- New unit tests for timeout behavior in `OllamaClientTimeoutTest`.

### Changed
- Integrated `HttpTimeout` plugin in `OllamaClient` to support custom and default timeouts.

## [0.1.2] - 2026-04-30

### Added
- **Added `ping()` method to `OllamaClient` for health checks.**

## [0.1.1] - 2026-04-29

### Added
- Dynamic configuration support via `configProvider` lambda in `OllamaClient`, allowing per-request host and header resolution.
- New factory function `OllamaClient(configProvider: suspend () -> OllamaClientConfig)` for easy dynamic client creation.
- New unit tests for dynamic configuration behavior in `OllamaClientDynamicConfigTest`.

### Changed
- Re-licensed the project from Apache 2.0 to **MIT License**.
- Updated `OllamaClient` internal architecture to support stateless request execution by resolving configuration at the call site.
- Refactored `OllamaClient` streaming methods (`chatStream`, `generateStream`, etc.) to be more efficient using `emitAll`.

## [0.1.0] - 2026-04-27

### Added
- Initial release of the Ollama Kotlin library.
- Core API support: Chat, Generate, Embed, Model Management, Blobs, and System endpoints.
- Support for streaming responses using Kotlin `Flow`.
- DSL-based configuration and Environment variable support (`OLLAMA_HOST`, `OLLAMA_API_KEY`).
- Comprehensive test suite for all major endpoints.

[Unreleased]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/0.1.3...HEAD
[0.1.3]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/tag/0.1.3
[0.1.2]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/tag/0.1.2
[0.1.1]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/tag/0.1.1
[0.1.0]: https://github.com/Udhay-Adithya/ollama-kotlin/releases/tag/0.1.0
