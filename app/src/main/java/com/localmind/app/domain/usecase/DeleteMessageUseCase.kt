package com.localmind.app.domain.usecase

import com.localmind.app.data.repository.ChatRepository
import javax.inject.Inject

class DeleteMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String) {
        chatRepository.deleteMessage(messageId)
    }
}
