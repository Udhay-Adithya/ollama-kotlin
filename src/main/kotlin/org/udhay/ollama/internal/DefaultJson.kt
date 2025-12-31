package org.udhay.ollama.internal

import kotlinx.serialization.json.Json

internal val DefaultJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

