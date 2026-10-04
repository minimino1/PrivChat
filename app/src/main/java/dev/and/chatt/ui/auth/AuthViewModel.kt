package dev.and.chatt.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.and.chatt.data.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var displayName by mutableStateOf("")
        private set
    var isRegisterMode by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val canSubmit: Boolean
        get() = !isLoading && email.contains("@") && password.length >= 6 &&
            (!isRegisterMode || displayName.isNotBlank())

    fun onEmailChange(value: String) {
        email = value.trim(); errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value; errorMessage = null
    }

    fun onDisplayNameChange(value: String) {
        displayName = value; errorMessage = null
    }

    fun toggleMode() {
        isRegisterMode = !isRegisterMode
        errorMessage = null
    }

    fun submit(onSuccess: () -> Unit) {
        if (!canSubmit) return
        isLoading = true
        viewModelScope.launch {
            val result = if (isRegisterMode) {
                repository.register(email, password, displayName)
            } else {
                repository.login(email, password)
            }
            isLoading = false
            result
                .onSuccess { onSuccess() }
                .onFailure { throwable ->
                    errorMessage = throwable.friendlyMessage()
                }
        }
    }
}

/** Firebase-Fehlermeldungen sind intern englisch – hier lesbar machen. */
fun Throwable.friendlyMessage(): String = when (message) {
    null, "" -> "Unbekannter Fehler"
    else -> when {
        message!!.contains("email address is already in use", true) ->
            "Diese E-Mail-Adresse wird bereits verwendet."
        message!!.contains("password is invalid", true) ||
            message!!.contains("wrong password", true) ||
            message!!.contains("invalid login credentials", true) ->
            "E-Mail oder Passwort stimmt nicht."
        message!!.contains("weak password", true) ->
            "Passwort braucht mindestens 6 Zeichen."
        message!!.contains("network", true) ->
            "Keine Netzwerkverbindung."
        message!!.contains("too many requests", true) ->
            "Zu viele Versuche – bitte kurz warten."
        else -> message!!.replaceFirstChar { it.uppercase() }
    }
}