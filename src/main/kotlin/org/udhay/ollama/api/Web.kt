package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /api/web_search` — perform a web search through Ollama.
 *
 * Requires authentication via `OLLAMA_API_KEY` or a custom `Authorization` header.
 *
 * @property query The search query string.
 * @property maxResults Maximum number of search results to return.
 */
@Serializable
public data class WebSearchRequest(
    val query: String,
    @SerialName("max_results")
    val maxResults: Int? = null,
)

/**
 * A single web search result.
 *
 * @property title Title of the web page.
 * @property url URL of the web page.
 * @property content Extracted text content from the page.
 */
@Serializable
public data class WebSearchResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
)

/**
 * Response from `POST /api/web_search`.
 *
 * @property results List of search results.
 * @property error Error message, if the search failed.
 */
@Serializable
public data class WebSearchResponse(
    val results: List<WebSearchResult> = emptyList(),
    val error: String? = null,
)

/**
 * Request body for `POST /api/web_fetch` — fetch a single web page through Ollama.
 *
 * @property url The URL of the page to fetch.
 */
@Serializable
public data class WebFetchRequest(
    val url: String,
)

/**
 * Response from `POST /api/web_fetch`.
 *
 * @property title Title of the fetched page.
 * @property url Final URL after any redirects.
 * @property content Extracted text content from the page.
 * @property links URLs of links found on the page.
 * @property error Error message, if the fetch failed.
 */
@Serializable
public data class WebFetchResponse(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
    val links: List<String>? = null,
    val error: String? = null,
)

