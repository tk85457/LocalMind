package com.localmind.app.llm.prompt

import com.localmind.app.domain.model.Model
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
class TemplateResolver @Inject constructor() {
    fun resolve(model: Model?, explicitSystemPrompt: String?): ResolvedTemplateProfile {
        val inferredTemplateId = TemplateCatalog.inferTemplateId(
            modelNameHint = model?.name.orEmpty(),
            repoIdHint = model?.id
        )
        val selectedTemplateId = model?.templateId?.takeIf { it.isNotBlank() } ?: inferredTemplateId
        val spec = TemplateCatalog.get(selectedTemplateId)

        val modelStops = parseStopTokens(model?.stopTokensJson)
        val mergedStops = (
            modelStops +
                spec.defaultStopTokens +
                TemplateCatalog.safeFallbackStops
            ).map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val resolvedSystemPrompt = explicitSystemPrompt
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: model?.recommendedSystemPrompt?.trim()?.takeIf { it.isNotEmpty() }
            ?: spec.defaultSystemPrompt

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

