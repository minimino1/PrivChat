package dev.and.chatt.data

import com.google.firebase.database.ServerValue

/**
 * Alle Modelle sind bewusst aus einfachen Skalaren (String/Long/Boolean) gebaut.
 * Das 1:1-Verhältnis zum JSON-Format der Realtime Database haelt das Datenmodell
 * auch fuer ein spaeteres Web-Frontend (firebase-js-sdk) direkt nutzbar.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String = "",
    val avatarUrl: String = "",
)

data class Conversation(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val memberIds: List<String>,
    val memberNames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    val lastMessageAt: Long = 0L,
    val lastReadAt: Long = 0L,
) {
    val hasUnread: Boolean get() = lastMessageAt > lastReadAt

    fun nameOf(uid: String): String = memberNames[uid] ?: uid.take(8)

    fun otherMembers(meUid: String): List<String> = memberIds.filter { it != meUid }
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val editedAt: Long? = null,
    val deleted: Boolean = false,
) {
    val isSystemMessage: Boolean get() = senderId == SYSTEM_SENDER

    companion object {
        const val SYSTEM_SENDER = "__system__"
    }
}

/** Mappt einen Firebase-Snapshot auf das Kotlin-Modell. */
object Parsers {

    fun toMap(value: Any?): Map<String, Any?> =
        (value as? Map<*, *>)?.entries
            ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
            ?.toMap()
            .orEmpty()

    fun user(snapshotId: String, value: Any?): UserProfile {
        val map = toMap(value)
        return UserProfile(
            uid = map["uid"] as? String ?: snapshotId,
            displayName = map["displayName"] as? String ?: "",
            email = map["email"] as? String ?: "",
            avatarUrl = map["avatarUrl"] as? String ?: "",
        )
    }

    fun conversation(id: String, value: Any?): Conversation {
        val map = toMap(value)
        val members = toMap(map["memberIds"]).filterValues { it == true }.keys.toList()
        val names = toMap(map["memberNames"]).mapNotNull { (uid, name) ->
            (name as? String)?.let { uid to it }
        }.toMap()
        return Conversation(
            id = map["id"] as? String ?: id,
            title = map["title"] as? String ?: "",
            isGroup = map["isGroup"] as? Boolean ?: false,
            memberIds = members,
            memberNames = names,
            lastMessageText = map["lastMessageText"] as? String ?: "",
            lastSenderId = map["lastSenderId"] as? String ?: "",
            lastMessageAt = (map["lastMessageAt"] as? Number)?.toLong() ?: 0L,
            lastReadAt = (map["lastReadAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    fun message(conversationId: String, id: String, value: Any?): Message {
        val map = toMap(value)
        return Message(
            id = map["id"] as? String ?: id,
            conversationId = conversationId,
            senderId = map["senderId"] as? String ?: "",
            text = map["text"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            editedAt = (map["editedAt"] as? Number)?.toLong(),
            deleted = map["deleted"] as? Boolean ?: false,
        )
    }

    /** Platzhalter, den die Realtime Database serverseitig in einen echten Zeitstempel umwandelt. */
    fun serverTimestamp(): Map<String, Any> = ServerValue.TIMESTAMP
}