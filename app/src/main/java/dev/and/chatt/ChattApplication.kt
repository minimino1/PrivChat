package dev.and.chatt

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import dev.and.chatt.data.FirebaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChattApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerFcmToken()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            getString(R.string.notification_channel_id),
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Neue Nachrichten" }
        manager.createNotificationChannel(channel)
    }

    /**
     * FCM-Token pro Nutzer und Geraet hinterlegen. Eine Cloud Function abonniert
     * diese Tokens spaeter auf die Chat-Themes (siehe functions/).
     */
    private fun registerFcmToken() {
        appScope.launch {
            val uid = FirebaseProvider.currentUid ?: return@launch
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .onSuccess { token ->
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
}