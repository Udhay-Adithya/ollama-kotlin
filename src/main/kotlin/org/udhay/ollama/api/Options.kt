package org.udhay.ollama.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * Runtime and load-time model options.
 *
 * Every field is optional and unset fields are omitted from the request, so only what you set is
 * sent. Options the server understands but this type does not yet name can be passed through
 * [extra], which keeps the type forward-compatible with new Ollama releases.
 *
 * ```kotlin
 * client.chat(
 *     ChatRequest(
 *         model = "llama3",
 *         messages = messages,
 *         options = Options(temperature = 0.7, topK = 40, numCtx = 8192),
 *     )
 * )
 * ```
 *
 * With a passthrough for something newer than this library:
 *
 * ```kotlin
 * Options(temperature = 0.2, extra = mapOf("some_new_option" to JsonPrimitive(true)))
 * ```
 *
 * @property numa Enable NUMA support.
 * @property numCtx Size of the context window in tokens.
 * @property numBatch Batch size for prompt processing.
 * @property numGpu Number of layers to offload to the GPU.
 * @property mainGpu Index of the GPU to use for small tensors.
 * @property lowVram Trade speed for a smaller VRAM footprint.
 * @property f16Kv Use 16-bit floats for the key/value cache.
 * @property logitsAll Return logits for every token, not just the last.
 * @property vocabOnly Load only the vocabulary, not the weights.
 * @property useMmap Memory-map the model file.
 * @property useMlock Lock the model in RAM to prevent swapping.
 * @property embeddingOnly Load the model for embeddings only.
 * @property numThread Number of CPU threads to use.
 * @property numKeep Tokens from the prompt to retain when the context is exceeded.
 * @property seed Random seed; the same seed with the same prompt gives the same output.
 * @property numPredict Maximum tokens to generate. `-1` for unlimited.
 * @property topK Sample from the K most likely tokens.
 * @property topP Sample from the smallest token set whose probability sums to P.
 * @property tfsZ Tail-free sampling parameter.
 * @property typicalP Locally typical sampling parameter.
 * @property repeatLastN Tokens of history considered for the repeat penalty.
 * @property temperature Sampling temperature. Higher is more random.
 * @property repeatPenalty Penalty applied to repeated tokens.
 * @property presencePenalty Penalty for tokens that already appeared.
 * @property frequencyPenalty Penalty scaled by how often a token appeared.
 * @property mirostat Mirostat sampling mode: `0` off, `1` v1, `2` v2.
 * @property mirostatTau Target entropy for Mirostat.
 * @property mirostatEta Learning rate for Mirostat.
 * @property penalizeNewline Apply the repeat penalty to newlines.
 * @property stop Sequences that stop generation when produced.
 * @property extra Options not named above, merged into the same JSON object.
 */
@Serializable
public data class Options(
    // Load-time options
    val numa: Boolean? = null,
    @SerialName("num_ctx") val numCtx: Int? = null,
    @SerialName("num_batch") val numBatch: Int? = null,
    @SerialName("num_gpu") val numGpu: Int? = null,
    @SerialName("main_gpu") val mainGpu: Int? = null,
    @SerialName("low_vram") val lowVram: Boolean? = null,
    @SerialName("f16_kv") val f16Kv: Boolean? = null,
    @SerialName("logits_all") val logitsAll: Boolean? = null,
    @SerialName("vocab_only") val vocabOnly: Boolean? = null,
    @SerialName("use_mmap") val useMmap: Boolean? = null,
    @SerialName("use_mlock") val useMlock: Boolean? = null,
    @SerialName("embedding_only") val embeddingOnly: Boolean? = null,
    @SerialName("num_thread") val numThread: Int? = null,

    // Runtime options
    @SerialName("num_keep") val numKeep: Int? = null,
    val seed: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("tfs_z") val tfsZ: Double? = null,
    @SerialName("typical_p") val typicalP: Double? = null,
    @SerialName("repeat_last_n") val repeatLastN: Int? = null,
    val temperature: Double? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    val mirostat: Int? = null,
    @SerialName("mirostat_tau") val mirostatTau: Double? = null,
    @SerialName("mirostat_eta") val mirostatEta: Double? = null,
    @SerialName("penalize_newline") val penalizeNewline: Boolean? = null,
    val stop: List<String>? = null,

    /** Options this library does not name yet, merged into the same JSON object. */
    val extra: Map<String, JsonElement> = emptyMap(),
)

/**
 * Serializes [Options] with [Options.extra] flattened into the surrounding object, so a passthrough
 * option lands as `{"temperature":0.2,"some_new_option":true}` rather than nested under `extra`.
 *
 * Applied at the use site rather than on the class itself — annotating [Options] with
 * `@Serializable(with = ...)` would make [Options.serializer] recurse into this transformer.
 */
public object OptionsSerializer : JsonTransformingSerializer<Options>(Options.serializer()) {

    private val knownKeys: Set<String> =
        (0 until Options.serializer().descriptor.elementsCount)
            .map { Options.serializer().descriptor.getElementName(it) }
            .toSet()

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val extra = obj["extra"]?.jsonObject.orEmpty()
        return JsonObject(obj - "extra" + extra)
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val (known, unknown) = obj.entries.partition { it.key in knownKeys }
        return JsonObject(
            known.associate { it.key to it.value } +
                ("extra" to JsonObject(unknown.associate { it.key to it.value })),
        )
    }
}

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
