package dev.and.chatt.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/** Schlanker Service-Locator statt DI-Framework (bewusst fuer den Projektumfang). */
object FirebaseProvider {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    const val PATH_USERS = "users"
    const val PATH_CONVERSATIONS = "conversations"
    const val PATH_USER_CONVERSATIONS = "userConversations"
    const val PATH_PRESENCE = "presence"
    const val PATH_TYPING = "typing"
    const val PATH_TOKENS = "fcmTokens"

    val currentUid: String? get() = auth.currentUser?.uid
}