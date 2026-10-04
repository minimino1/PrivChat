package dev.and.chatt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.and.chatt.data.AuthRepository
import dev.and.chatt.data.FirebaseProvider
import dev.and.chatt.data.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object SignedIn : SessionState
}

/**
 * Haelt den globalen Login-Status. Solange eingeloggt, wird zusaetzlich die
 * Praesenz gepflegt (onDisconnect setzt sie beim App-Absturz automatisch auf offline).
 */
class SessionViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    val state: StateFlow<SessionState> = authRepository.authState()
        .map { user ->
            val uid = user?.uid
            when (uid) {
                null -> SessionState.SignedOut
                else -> {
                    userRepository.setOnline(uid, online = true)
                    SessionState.SignedIn
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    fun signOut() {
        viewModelScope.launch { runCatching { authRepository.logout() } }
    }

    fun currentUid(): String? = FirebaseProvider.currentUid
}