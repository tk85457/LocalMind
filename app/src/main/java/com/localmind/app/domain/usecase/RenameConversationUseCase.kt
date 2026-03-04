package com.localmind.app.domain.usecase

import com.localmind.app.data.repository.ChatRepository
import javax.inject.Inject

class RenameConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(conversationId: String, newTitle: String) {
        chatRepository.renameConversation(conversationId, newTitle)
    }
}
