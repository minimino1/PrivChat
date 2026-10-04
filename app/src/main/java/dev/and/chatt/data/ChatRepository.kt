package dev.and.chatt.data

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {

    private val database get() = FirebaseProvider.database

    // ---------------------------------------------------------------- Konversationen

    /**
     * Jeder Nutzer hat einen eigenen Index unter /userConversations. Dadurch braucht die
     * Chatliste genau EINEN Listener statt einem pro Konversation (und die Security Rules
     * koennen sauber pro Nutzer filtern).
     */
    fun observeConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_USER_CONVERSATIONS).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val conversations = snapshot.children
                    .mapNotNull { Parsers.conversation(it.key ?: "", it.value) }
                    .sortedByDescending { it.lastMessageAt }
                trySend(conversations)
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeConversation(uid: String, conversationId: String): Flow<Conversation?> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_USER_CONVERSATIONS)
            .child(uid).child(conversationId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                trySend(if (snapshot.exists()) Parsers.conversation(conversationId, snapshot.value) else null)
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Stabile ID fuer 1:1-Chats: sorted UIDs -> "dm_a_b". So finden sich beide Seiten wieder. */
    fun directConversationId(uidA: String, uidB: String): String {
        val (a, b) = listOf(uidA, uidB).sorted()
        return "dm_${a}_$b"
    }

    suspend fun openDirectConversation(
        myUid: String,
        myName: String,
        other: UserProfile,
    ): String {
        val id = directConversationId(myUid, other.uid)
        val existing = database.reference.child(FirebaseProvider.PATH_CONVERSATIONS).child(id).get().await()
        if (existing.exists()) return id

        val now = Parsers.serverTimestamp()
        val members = mapOf(myUid to true, other.uid to true)
        val memberNames = mapOf(myUid to myName, other.uid to other.displayName)

        database.reference.child(FirebaseProvider.PATH_CONVERSATIONS).child(id).updateChildren(
            mapOf(
                "id" to id,
                "isGroup" to false,
                "title" to other.displayName,   // wird beim Namenwechsel aktualisiert
                "memberIds" to members,
                "memberNames" to memberNames,
                "createdBy" to myUid,
                "createdAt" to now,
                "updatedAt" to now,
            )
        ).await()

        writeIndexFor(
            conversationId = id,
            title = other.displayName,
            memberIds = members.keys.toList(),
            memberNames = memberNames,
            senderId = myUid,
            lastText = "",
            lastAt = now,
        )
        return id
    }

    suspend fun createGroupConversation(
        myUid: String,
        myName: String,
        title: String,
        participants: List<UserProfile>,
    ): String {
        val ref = database.reference.child(FirebaseProvider.PATH_CONVERSATIONS).push()
        val id = ref.key ?: error("Konnte keine Konversations-ID erzeugen")
        val members = buildMap {
            put(myUid, true)
            participants.forEach { put(it.uid, true) }
        }
        val memberNames = buildMap {
            put(myUid, myName)
            participants.forEach { put(it.uid, it.displayName) }
        }
        val now = Parsers.serverTimestamp()

        ref.updateChildren(
            mapOf(
                "id" to id,
                "isGroup" to true,
                "title" to title,
                "memberIds" to members,
                "memberNames" to memberNames,
                "createdBy" to myUid,
                "createdAt" to now,
                "updatedAt" to now,
            )
        ).await()

        writeIndexFor(
            conversationId = id,
            title = title,
            memberIds = members.keys.toList(),
            memberNames = memberNames,
            senderId = myUid,
            lastText = "",
            lastAt = now,
        )
        return id
    }

    private suspend fun writeIndexFor(
        conversationId: String,
        title: String,
        memberIds: List<String>,
        memberNames: Map<String, String>,
        senderId: String,
        lastText: String?,
        lastAt: Any,
    ) {
        val index = database.reference.child(FirebaseProvider.PATH_USER_CONVERSATIONS)
        val updates = buildMap<String, Any?> {
            for (uid in memberIds) {
                val entry = mutableMapOf<String, Any?>(
                    "id" to conversationId,
                    "title" to title,
                    "isGroup" to memberIds.size > 2,
                    "memberIds" to memberIds.associateWith { true },
                    "memberNames" to memberNames,
                    "lastSenderId" to senderId,
                    "lastMessageText" to (lastText ?: ""),
                    "lastMessageAt" to lastAt,
                )
                // Nur der Absender darf lastReadAt schreiben (Security Rules) – und er
                // hat den Chat gerade geoeffnet bzw. die Nachricht selbst gesehen.
                if (uid == senderId) entry["lastReadAt"] = lastAt
                put("$uid/$conversationId", entry)
            }
        }
        index.updateChildren(updates).await()
    }

    // ---------------------------------------------------------------- Nachrichten

    fun observeMessages(conversationId: String, limit: Long = 100): Flow<List<Message>> = callbackFlow {
        val query: Query = database.reference
            .child(FirebaseProvider.PATH_CONVERSATIONS).child(conversationId).child("messages")
            .orderByChild("createdAt")
            .limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val messages = snapshot.children
                    .map { Parsers.message(conversationId, it.key ?: "", it.value) }
                    .sortedBy { it.createdAt }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val messageRef = database.reference.child(FirebaseProvider.PATH_CONVERSATIONS)
            .child(conversationId).child("messages").push()
        val now = Parsers.serverTimestamp()

        messageRef.updateChildren(
            mapOf(
                "id" to messageRef.key,
                "senderId" to senderId,
                "text" to trimmed,
                "type" to "text",
                "createdAt" to now,
            )
        ).await()

        // Index (Chatliste) fuer alle Mitgliedern aktualisieren.
        val snapshot = database.reference.child(FirebaseProvider.PATH_CONVERSATIONS)
            .child(conversationId).get().await()
        val members = Parsers.toMap(snapshot.child("memberIds").value).filterValues { it == true }.keys.toList()
        val memberNames = Parsers.toMap(snapshot.child("memberNames").value)

        val conversationUpdate = mapOf<String, Any?>(
            "lastMessageText" to trimmed,
            "lastSenderId" to senderId,
            "lastMessageAt" to now,
            "updatedAt" to now,
        )
        database.reference.child(FirebaseProvider.PATH_CONVERSATIONS).child(conversationId)
            .updateChildren(conversationUpdate).await()

        writeIndexFor(
            conversationId = conversationId,
            title = snapshot.child("title").value as? String ?: "",
            memberIds = members,
            memberNames = memberNames.mapValues { it.value.toString() },
            senderId = senderId,
            lastText = trimmed,
            lastAt = now,
        )
    }

    suspend fun editMessage(conversationId: String, messageId: String, text: String) {
        database.reference.child(FirebaseProvider.PATH_CONVERSATIONS)
            .child(conversationId).child("messages").child(messageId)
            .updateChildren(mapOf("text" to text.trim(), "editedAt" to Parsers.serverTimestamp()))
            .await()
    }

    /** Loescht nur fuer den Sender (Security Rules pruefen das serverseitig erneut). */
    suspend fun deleteMessage(conversationId: String, messageId: String) {
        database.reference.child(FirebaseProvider.PATH_CONVERSATIONS)
            .child(conversationId).child("messages").child(messageId)
            .updateChildren(
                mapOf(
                    "text" to "",
                    "deleted" to true,
                    "editedAt" to Parsers.serverTimestamp(),
                )
            ).await()
    }

    // ---------------------------------------------------------------- Gelesen / Tippen

    suspend fun markAsRead(conversationId: String, uid: String) {
        database.reference.child(FirebaseProvider.PATH_USER_CONVERSATIONS)
            .child(uid).child(conversationId)
            .updateChildren(mapOf("lastReadAt" to Parsers.serverTimestamp())).await()
    }

    fun setTyping(conversationId: String, uid: String, typing: Boolean) {
        val ref = database.reference.child(FirebaseProvider.PATH_TYPING).child(conversationId).child(uid)
        if (typing) {
            ref.onDisconnect().removeValue()
            ref.setValue(true)
        } else {
            ref.removeValue()
        }
    }

    fun observeTyping(conversationId: String, myUid: String): Flow<Set<String>> = callbackFlow {
        val ref = database.reference.child(FirebaseProvider.PATH_TYPING).child(conversationId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val typing = snapshot.children
                    .mapNotNull { child ->
                        if (child.key != myUid && child.value == true) child.key else null
                    }.toSet()
                trySend(typing)
            }

            override fun onCancelled(error: DatabaseError) = close(error.toException())
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}