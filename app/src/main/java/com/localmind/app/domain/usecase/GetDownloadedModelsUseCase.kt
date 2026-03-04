package com.localmind.app.domain.usecase

import com.localmind.app.domain.model.Model
import com.localmind.app.data.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadedModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(): Flow<List<Model>> {
        return modelRepository.getAllModels()
    }
}
