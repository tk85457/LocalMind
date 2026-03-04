package com.localmind.app.llm

import com.localmind.app.data.remote.RemoteChatCompletionRequest
import com.localmind.app.data.remote.RemoteChatMessage
import com.localmind.app.data.remote.RemoteInferenceApi
import com.localmind.app.data.repository.ModelRepository
import com.localmind.app.data.repository.SettingsRepository
import com.localmind.app.domain.model.InferenceSource
import com.localmind.app.domain.model.RemoteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RemoteInferenceEngine @Inject constructor(
    @Named("remoteInferenceApi") private val remoteInferenceApi: RemoteInferenceApi,
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val errorMapper: InferenceErrorMapper
) : ChatInferenceEngine {

    override val source: InferenceSource = InferenceSource.REMOTE

    override fun generate(
        prompt: String,
        config: InferenceConfig,
        shouldUpdateCache: Boolean,
        remoteModelOverride: String?
    ): Flow<GenerationResult> {
        return flow {
            emit(GenerationResult.Started)

            val provider = settingsRepository.remoteProvider.first()
            if (provider != RemoteProvider.HUGGING_FACE) {
                emit(GenerationResult.Error("Selected remote provider is not configured yet"))
                return@flow
            }
            val activeCloudRepoId = settingsRepository.activeCloudModelRepoId.first()?.takeIf { it.isNotBlank() }

            val activeModel = modelRepository.getActiveModel()
            val remoteModel = remoteModelOverride?.takeIf { it.isNotBlank() }
                ?: activeCloudRepoId
                ?: activeModel?.id?.takeIf { it.contains("/") }
                ?: DEFAULT_REMOTE_MODEL

            val response = remoteInferenceApi.createChatCompletion(
                RemoteChatCompletionRequest(
                    model = remoteModel,
                    messages = listOf(RemoteChatMessage(role = "user", content = prompt)),
                    temperature = config.temperature,
                    topP = config.topP,
                    maxTokens = config.maxTokens,
                    stream = false
                )
            )
            val text = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (text.isBlank()) {
                emit(GenerationResult.Error("Remote response was empty"))
                return@flow
            }

            text.chunked(24).forEach { chunk ->
                emit(GenerationResult.Token(chunk))
            }
            emit(GenerationResult.Complete)
        }
            .catch { throwable ->
                emit(GenerationResult.Error(errorMapper.map(throwable)))
            }
            .flowOn(Dispatchers.IO)
    }

    private companion object {
        private const val DEFAULT_REMOTE_MODEL = "Qwen/Qwen2.5-1.5B-Instruct"
    }
}
