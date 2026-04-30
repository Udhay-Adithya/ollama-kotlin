# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.1.2]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/tag/0.1.2
[0.1.1]: https://github.com/Udhay-Adithya/ollama-kotlin/compare/tag/0.1.1
[0.1.0]: https://github.com/Udhay-Adithya/ollama-kotlin/releases/tag/0.1.0
