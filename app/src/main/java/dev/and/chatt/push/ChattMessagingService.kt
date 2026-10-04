package dev.and.chatt.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.and.chatt.MainActivity
import dev.and.chatt.R
import dev.and.chatt.data.FirebaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Empfaengt Push-Nachrichten. Die Cloud Function (functions/index.js) schickt
 * ausschliesslich Data-Messages, damit die App die Benachrichtigung selbst
 * rendern kann (Deep-Link in den richtigen Chat).
 */
class ChattMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = FirebaseProvider.currentUid ?: return
        scope.launch {
            runCatching {
                // FCM-Tokens sind nicht als Schluessel geeignet -> als Key hashen.
                val key = Integer.toHexString(token.hashCode())
                FirebaseProvider.database.reference
                    .child(FirebaseProvider.PATH_TOKENS)
                    .child(uid).child(key)
                    .updateChildren(
                        mapOf(
                            "token" to token,
                            "platform" to "android",
                            "updatedAt" to ServerValue.TIMESTAMP,
                        )
                    ).await()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val conversationId = data["conversationId"] ?: return
        // Eigene Nachrichten nicht als Push anzeigen.
        if (data["senderId"] == FirebaseProvider.currentUid) return

        val title = data["title"] ?: "Neue Nachricht"
        val text = data["text"].orEmpty()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canNotify = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val notification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService<NotificationManager>()?.notify(conversationId.hashCode(), notification)
    }
}