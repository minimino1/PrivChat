package dev.and.chatt.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.and.chatt.data.AuthRepository
import dev.and.chatt.data.FirebaseProvider
import dev.and.chatt.data.UserProfile
import dev.and.chatt.data.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()

    private val uid: String = FirebaseProvider.currentUid.orEmpty()

    val profile: StateFlow<UserProfile?> = userRepository
        .observeProfile(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var displayName by mutableStateOf("")
        private set
    var isSaving by mutableStateOf(false)
        private set
    var savedConfirmation by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onDisplayNameChange(value: String) {
        displayName = value
        savedConfirmation = false
    }

    fun save() {
        val name = displayName.trim()
        if (name.isBlank() || isSaving) return
        isSaving = true
        viewModelScope.launch {
            runCatching { userRepository.updateDisplayName(uid, name) }
                .onSuccess { savedConfirmation = true }
                .onFailure { errorMessage = it.message }
            isSaving = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { authRepository.logout() }
                .onFailure { errorMessage = it.message }
            onDone()
        }
    }

    fun dismissError() {
        errorMessage = null
    }
}