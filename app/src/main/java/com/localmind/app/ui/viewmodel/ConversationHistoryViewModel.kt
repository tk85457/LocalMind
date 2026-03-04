package com.localmind.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmind.app.data.repository.ChatRepository
import com.localmind.app.domain.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationHistoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            chatRepository.getAllConversations()
                .onEach { convList ->
                    _conversations.value = convList.sortedByDescending { it.updatedAt }
                    _isLoading.value = false
                }
                .catch {
                    _conversations.value = emptyList()
                    _isLoading.value = false
                }
                .collect()
        }
    }

    fun groupConversations(conversations: List<Conversation>): Map<String, List<Conversation>> {
        val groups = conversations.groupBy { conv ->
            val date = java.time.Instant.ofEpochMilli(conv.updatedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val today = java.time.LocalDate.now()
            val yesterday = today.minusDays(1)
            val lastWeek = today.minusDays(7)

            when {
                date.isEqual(today) -> "Today"
                date.isEqual(yesterday) -> "Yesterday"
                date.isAfter(lastWeek) -> "Previous 7 Days"
                else -> "Older"
            }
        }

        val result = mutableMapOf<String, List<Conversation>>()
        listOf("Today", "Yesterday", "Previous 7 Days", "Older").forEach { key ->
            groups[key]?.let { if (it.isNotEmpty()) result[key] = it }
        }
        return result
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameConversation(conversationId, newTitle)
        }
    }
}
