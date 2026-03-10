package com.localmind.app.llm.prompt

import android.util.Log
import com.localmind.app.domain.model.Model
import com.localmind.app.llm.nativelib.LlamaCppBridge
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedTemplateProfile(
    val templateId: String,
    val family: TemplateFamily,
    val systemPrompt: String,
    val stopTokens: List<String>
)

@Singleton
class TemplateResolver @Inject constructor(
    private val bridge: LlamaCppBridge
) {
    companion object {
        private const val TAG = "TemplateResolver"

        /**
         * POCKETPAL FIX: Dynamic EOS stop token detection from GGUF metadata.
         * Model ka hparams map check karo — tokenizer.ggml.eos_token_id ya
         * tokenizer.ggml.eot_token_id key hoti hai.
         * Ye important hai kyunki different models alag EOS tokens use karte hain.
         * PocketPal bhi yahi approach use karta hai — metadata se eos detect karo.
         */
        fun extractEosTokensFromMetadata(hparams: Map<String, String>): List<String> {
            val eosTokens = mutableListOf<String>()

            // EOS token string directly check karo
            val eosStr = hparams["tokenizer.ggml.eos_token"]?.trim()
            if (!eosStr.isNullOrBlank()) {
                eosTokens += eosStr
                Log.d(TAG, "Dynamic EOS from metadata: $eosStr")
            }

            // EOT token string check karo
            val eotStr = hparams["tokenizer.ggml.eot_token"]?.trim()
            if (!eotStr.isNullOrBlank() && eotStr !in eosTokens) {
                eosTokens += eotStr
                Log.d(TAG, "Dynamic EOT from metadata: $eotStr")
            }

            // Common known EOS token IDs se string map karo
            // (jab string directly available na ho, ID se infer karo)
            val eosTokenId = hparams["tokenizer.ggml.eos_token_id"]?.trim()?.toIntOrNull()
            if (eosTokenId != null && eosTokens.isEmpty()) {
                // Common EOS token ID to string mapping
                val knownEosById = mapOf(
                    2 to "</s>",         // LLaMA-1, Mistral
                    32000 to "</s>",     // LLaMA-2 variants
                    32021 to "<|end|>",  // Phi-3
                    32007 to "<|end|>",  // Phi-3 alternate
                    151643 to "<|endoftext|>", // Qwen
                    128009 to "<|eot_id|>",   // LLaMA-3
                    107 to "<end_of_turn>"     // Gemma
                )
                knownEosById[eosTokenId]?.let { mapped ->
                    eosTokens += mapped
                    Log.d(TAG, "Mapped EOS from token_id=$eosTokenId -> $mapped")
                }
            }

            return eosTokens
        }
    }

    fun resolve(model: Model?, explicitSystemPrompt: String?): ResolvedTemplateProfile {
        return resolveWithModelPath(model, explicitSystemPrompt, modelPath = null)
    }

    /**
     * Resolve template profile with optional dynamic EOS detection from model file.
     * Agar modelPath diya gaya hai, GGUF metadata se EOS tokens automatically detect honge.
     */
    fun resolveWithModelPath(
        model: Model?,
        explicitSystemPrompt: String?,
        @Suppress("UNUSED_PARAMETER") modelPath: String?
    ): ResolvedTemplateProfile {
        val inferredTemplateId = TemplateCatalog.inferTemplateId(
            modelNameHint = model?.name.orEmpty(),
            repoIdHint = model?.id
        )
        val selectedTemplateId = model?.templateId?.takeIf { it.isNotBlank() } ?: inferredTemplateId
        val spec = TemplateCatalog.get(selectedTemplateId)

        val modelStops = parseStopTokens(model?.stopTokensJson)

        // Native GGUF metadata reads are disabled on Android in this build because
        // some models abort inside llama.cpp during vocab-only metadata loads.
        val dynamicEosTokens = emptyList<String>()

        val mergedStops = (
            modelStops +
                dynamicEosTokens +
                spec.defaultStopTokens +
                TemplateCatalog.safeFallbackStops
            ).map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // PERF: System prompt priority: explicit (persona) > model recommended > empty.
        // Koi hardcoded fallback nahi — extra tokens = slow TTFT.
        // DEEPSEEK R1 FIX: R1 distill models (especially 1.5B) system prompt se
        // confuse hoke garbage (@@@@@ etc.) generate karte hain.
        // Known issue: https://github.com/ggml-org/llama.cpp/issues/10781
        // Fix: DeepSeek R1 ke liye system prompt force-empty karo.
        val isDeepSeekR1 = spec.id == TemplateCatalog.TEMPLATE_DEEPSEEK_R1
        val resolvedSystemPrompt = if (isDeepSeekR1) {
            "" // DeepSeek R1: no system prompt — model expects empty system
        } else {
            explicitSystemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: model?.recommendedSystemPrompt?.trim()?.takeIf { it.isNotEmpty() }
                ?: ""
        }

        return ResolvedTemplateProfile(
            templateId = spec.id,
            family = spec.family,
            systemPrompt = resolvedSystemPrompt,
            stopTokens = mergedStops
        )
    }

    private fun parseStopTokens(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }
}
