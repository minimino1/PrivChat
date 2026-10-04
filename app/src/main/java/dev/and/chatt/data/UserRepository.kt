package dev.and.chatt.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val database get() = FirebaseProvider.database

    /** Legt den Profilsatz nach der Registrierung an. */
    suspend fun createProfile(user: FirebaseUser) {
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@").orEmpty()
            .ifBlank { "Nutzer" }
        database.reference.child(FirebaseProvider.PATH_USERS).child(user.uid)
            .updateChildren(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "email" to (user.email ?: ""),
                    "createdAt" to Parsers.serverTimestamp(),
                )
            ).await()
    }

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_USERS).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                trySend(
                    if (snapshot.exists()) Parsers.user(uid, snapshot.value) else null
                )
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Einfache Suche über /users – für kleine Nutzerzahlen völlig ausreichend. */
    fun searchUsers(query: String): Flow<List<UserProfile>> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_USERS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val term = query.trim().lowercase()
                val users = snapshot.children
                    .mapNotNull { Parsers.user(it.key ?: "", it.value) }
                    .filter { term.isEmpty() || it.displayName.lowercase().contains(term) }
                    .sortedBy { it.displayName.lowercase() }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateDisplayName(uid: String, displayName: String) {
        database.reference.child(FirebaseProvider.PATH_USERS).child(uid)
            .updateChildren(mapOf("displayName" to displayName)).await()
        // Anzeigename auch in bestehenden 1:1-Chats spiegeln.
        val convRef = database.reference.child(FirebaseProvider.PATH_USER_CONVERSATIONS).child(uid)
        val snapshot = convRef.get().await()
        val updates = buildMap<String, Any?> {
            snapshot.children.map { it.key }.filterNotNull().forEach { convId ->
                val members = Parsers.toMap(snapshot.child(convId).child("memberIds").value)
                if (members[uid] == true) {
                    put("$convId/memberNames/$uid", displayName)
                    if (members.size == 2) put("$convId/title", displayName)
                }
            }
        }
        if (updates.isNotEmpty()) convRef.updateChildren(updates).await()
    }

    /** Präsenz mit onDisconnect: wird automatisch auf "offline" gesetzt, wenn die App stirbt. */
    fun setOnline(uid: String, online: Boolean) {
        val ref = database.reference.child(FirebaseProvider.PATH_PRESENCE).child(uid)
        val value = mapOf(
            "state" to if (online) "online" else "offline",
            "lastSeen" to Parsers.serverTimestamp(),
        )
        if (online) {
            ref.onDisconnect().updateChildren(value)
            ref.updateChildren(value)
        } else {
            ref.updateChildren(value)
        }
    }

    fun observePresence(uid: String): Flow<Boolean> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_PRESENCE).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                trySend(snapshot.child("state").value == "online")
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}