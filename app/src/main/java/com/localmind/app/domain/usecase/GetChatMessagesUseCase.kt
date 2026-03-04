package com.localmind.app.domain.usecase

import com.localmind.app.domain.model.Message
import com.localmind.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> {
        return chatRepository.getMessagesByConversation(conversationId)
    }
}
