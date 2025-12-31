package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebSearchRequest(
    val query: String,
    val maxResults: Int? = null,
)

@Serializable
data class WebSearchResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
)

@Serializable
data class WebSearchResponse(
    val results: List<WebSearchResult> = emptyList(),
    val error: String? = null,
)

@Serializable
data class WebFetchRequest(
    val url: String,
    val maxLength: Int? = null,
)

@Serializable
data class WebFetchResponse(
    val url: String? = null,
    val content: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val error: String? = null,
    val metadata: JsonElement? = null,
)

