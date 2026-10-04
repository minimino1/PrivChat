package dev.and.chatt.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.and.chatt.data.ChatRepository
import dev.and.chatt.data.Conversation
import dev.and.chatt.data.FirebaseProvider
import dev.and.chatt.data.Message
import dev.and.chatt.data.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val conversationId: String) : ViewModel() {

    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()

    private val uid: String = FirebaseProvider.currentUid.orEmpty()

    val messages: StateFlow<List<Message>> = chatRepository
        .observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversation: StateFlow<Conversation?> = chatRepository
        .observeConversation(uid, conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val typingUserIds: StateFlow<Set<String>> = chatRepository
        .observeTyping(conversationId, uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Online-Status der Gegenstelle – bei Gruppen-Chats gibt es keine klare Aussage, daher nur 1:1. */
    val otherUserOnline: StateFlow<Boolean> = flow {
        val current = chatRepository.observeConversation(uid, conversationId).first()
        val otherId = current?.otherMembers(uid)?.firstOrNull()
        if (otherId == null) {
            emit(false)
        } else {
            userRepository.observePresence(otherId).collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var draft by mutableStateOf("")
        private set
    var editingMessage by mutableStateOf<Message?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        markRead()
    }

    fun onDraftChange(value: String) {
        draft = value
        if (editingMessage != null && value.isBlank()) editingMessage = null
        viewModelScope.launch {
            chatRepository.setTyping(conversationId, uid, typing = value.isNotBlank())
        }
    }

    fun markRead() {
        viewModelScope.launch {
            runCatching { chatRepository.markAsRead(conversationId, uid) }
        }
    }

    fun send() {
        val editing = editingMessage
        if (draft.isBlank()) return
        viewModelScope.launch {
            runCatching {
                if (editing != null) {
                    chatRepository.editMessage(conversationId, editing.id, draft)
                } else {
                    chatRepository.sendMessage(conversationId, uid, draft)
                }
            }.onFailure { errorMessage = it.message }
            draft = ""
            editingMessage = null
            chatRepository.setTyping(conversationId, uid, typing = false)
        }
    }

    fun startEditing(message: Message) {
        if (message.senderId != uid || message.deleted) return
        editingMessage = message
        draft = message.text
    }

    fun cancelEditing() {
        editingMessage = null
        draft = ""
    }

    fun delete(message: Message) {
        if (message.senderId != uid) return
        viewModelScope.launch {
            runCatching { chatRepository.deleteMessage(conversationId, message.id) }
                .onFailure { errorMessage = it.message }
        }
    }

    fun dismissError() {
        errorMessage = null
    }

    companion object {
        fun factory(conversationId: String) = viewModelFactory {
            initializer { ChatViewModel(conversationId) }
        }
    }
}