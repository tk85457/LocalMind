package com.localmind.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface RemoteInferenceApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: RemoteChatCompletionRequest
    ): RemoteChatCompletionResponse
}

data class RemoteChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<RemoteChatMessage>,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("top_p") val topP: Float,
    @SerializedName("max_tokens") val maxTokens: Int,
    @SerializedName("stream") val stream: Boolean = false
)

data class RemoteChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class RemoteChatCompletionResponse(
    @SerializedName("choices") val choices: List<RemoteChoice> = emptyList()
)

data class RemoteChoice(
    @SerializedName("message") val message: RemoteAssistantMessage? = null
)

data class RemoteAssistantMessage(
    @SerializedName("content") val content: String? = null
)
