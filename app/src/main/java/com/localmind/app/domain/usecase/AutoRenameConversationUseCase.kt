package com.localmind.app.domain.usecase

import com.localmind.app.core.utils.ConversationTitleGenerator
import com.localmind.app.data.repository.ChatRepository
import javax.inject.Inject

class AutoRenameConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun invoke(
        conversationId: String,
        currentTitle: String?,
        firstUserMessage: String
    ) {
        val isDefault = currentTitle == null ||
                        currentTitle.isBlank() ||
                        currentTitle.startsWith("New Chat", ignoreCase = true) ||
                        currentTitle.startsWith("Conversation", ignoreCase = true)

        if (isDefault) {
            // FIX: Use ConversationTitleGenerator to clean up markdown, code blocks,
            // stop-words, and produce a readable title rather than raw-truncating
            // the first 40 chars (which could include backticks, code, etc.).
            val title = ConversationTitleGenerator.generate(firstUserMessage)
            if (title.isNotEmpty() && title != "New Chat") {
                chatRepository.renameConversation(conversationId, title)
            }
        }
    }
}
