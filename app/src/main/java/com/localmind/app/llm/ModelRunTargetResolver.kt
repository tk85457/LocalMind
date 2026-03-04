package com.localmind.app.llm

import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.domain.model.ModelRunTarget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRunTargetResolver @Inject constructor() {

    fun resolve(@Suppress("UNUSED_PARAMETER") model: ModelCatalogItem): ModelRunTarget = ModelRunTarget.LOCAL
}
