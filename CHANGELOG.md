# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `OllamaClientConfig.webHost` (default `https://ollama.com`) for routing the hosted web API
  through a proxy or gateway.

### Fixed
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
