package dev.and.chatt.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.and.chatt.data.ChatRepository
import dev.and.chatt.data.Conversation
import dev.and.chatt.data.FirebaseProvider
import dev.and.chatt.data.UserProfile
import dev.and.chatt.data.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel : ViewModel() {

    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()

    private val uid: String = FirebaseProvider.currentUid.orEmpty()

    val conversations: StateFlow<List<Conversation>> = chatRepository
        .observeConversations(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Eigenes Profil – wird im Avatar der Toolbar angezeigt. */
    val myProfile: StateFlow<UserProfile?> = userRepository
        .observeProfile(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val searchQuery = MutableStateFlow("")

    val users: StateFlow<List<UserProfile>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else userRepository.searchUsers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var showNewChat by mutableStateOf(false)
        private set
    var searchText by mutableStateOf("")
        private set
    var groupTitle by mutableStateOf("")
        private set
    var selectedParticipants by mutableStateOf(emptySet<UserProfile>())
        private set
    var isWorking by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val _openConversationId = MutableStateFlow<String?>(null)
    val openConversationId: StateFlow<String?> = _openConversationId.asStateFlow()

    fun openNewChat() {
        showNewChat = true
        errorMessage = null
    }

    fun closeNewChat() {
        showNewChat = false
        searchText = ""
        groupTitle = ""
        selectedParticipants = emptySet()
        errorMessage = null
        searchQuery.value = ""
    }

    fun onSearchChange(value: String) {
        searchText = value
        searchQuery.value = value
    }

    fun onGroupTitleChange(value: String) {
        groupTitle = value
    }

    fun toggleParticipant(user: UserProfile) {
        selectedParticipants = if (user.uid in selectedParticipants) {
            selectedParticipants - user
        } else {
            selectedParticipants + user
        }
    }

    fun startDirectChat(user: UserProfile) {
        if (isWorking || user.uid == uid) return
        isWorking = true
        viewModelScope.launch {
            runCatching { chatRepository.openDirectConversation(uid, myDisplayName(), user) }
                .onSuccess { id ->
                    closeNewChat()
                    _openConversationId.value = id
                }
                .onFailure { errorMessage = it.message }
            isWorking = false
        }
    }

    fun createGroup() {
        if (isWorking || groupTitle.isBlank() || selectedParticipants.isEmpty()) return
        isWorking = true
        viewModelScope.launch {
            runCatching {
                chatRepository.createGroupConversation(
                    myUid = uid,
                    myName = myDisplayName(),
                    title = groupTitle.trim(),
                    participants = selectedParticipants.toList(),
                )
            }.onSuccess { id ->
                closeNewChat()
                _openConversationId.value = id
            }.onFailure { errorMessage = it.message }
            isWorking = false
        }
    }

    fun consumeNavigation() {
        _openConversationId.value = null
    }

    private suspend fun myDisplayName(): String =
        userRepository.observeProfile(uid).first()?.displayName?.takeIf { it.isNotBlank() } ?: "Ich"
}