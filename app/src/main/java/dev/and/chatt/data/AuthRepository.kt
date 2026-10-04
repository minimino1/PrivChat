package dev.and.chatt.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.authStateChanged
import com.google.firebase.auth.updateProfile
import kotlinx.coroutines.flow.Flow

/**
 * Authentifizierung bewusst ueber E-Mail/Passwort: damit lassen sich auf EINEM
 * Geraet zwei Test-Accounts anlegen (zwei Accounts, kein zweites Handy noetig).
 */
class AuthRepository {

    private val auth get() = FirebaseProvider.auth
    private val userRepository = UserRepository()

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = auth.authStateChanged()

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> =
        runCatching {
            val created = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(created.user) { "Konto konnte nicht angelegt werden" }
            if (displayName.isNotBlank()) {
                user.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName.trim())
                        .build()
                ).await()
            }
            userRepository.createProfile(user)
        }

    suspend fun login(email: String, password: String): Result<Unit> =
        runCatching { auth.signInWithEmailAndPassword(email.trim(), password).await() }

    suspend fun logout() {
        currentUser?.let { userRepository.setOnline(it.uid, online = false) }
        auth.signOut()
    }
}