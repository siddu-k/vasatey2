
package com.sriox.vasatey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token received: $token")
        saveTokenToSupabase(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // This part remains unchanged as it just handles the incoming data payload.
        val data = remoteMessage.data
        showGuardianAlert(
            title = data["title"],
            body = data["body"],
            userName = data["username"],
            userEmail = data["email"],
            mobileNumber = data["mobileNumber"],
            latitude = data["latitude"],
            longitude = data["longitude"]
        )
    }

    private fun saveTokenToSupabase(token: String) {
        serviceScope.launch {
            try {
                val user = SupabaseInstance.client.auth.currentUserOrNull()
                if (user != null && user.email != null) {
                    val updates = mapOf("fcm_token" to token)
                    SupabaseInstance.client.from("users").update(updates) {
                        filter {
                            eq("email", user.email!!)
                        }
                    }
                    Log.d("FCM", "FCM token updated successfully in Supabase.")
                }
            } catch (e: Exception) {
                Log.w("FCM", "Error updating FCM token in Supabase", e)
            }
        }
    }

    private fun showGuardianAlert(title: String?, body: String?, userName: String?, userEmail: String?, mobileNumber: String?, latitude: String?, longitude: String?) {
        val channelId = "guardian_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Guardian Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts from people you are guarding."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val uniqueId = System.currentTimeMillis().toInt()

        val detailsIntent = Intent(this, AlertDetailsActivity::class.java).apply {
            putExtra("USER_NAME", userName)
            putExtra("USER_EMAIL", userEmail)
            putExtra("USER_MOBILE", mobileNumber)
            putExtra("USER_LATITUDE", latitude)
            putExtra("USER_LONGITUDE", longitude)
        }

        val detailsPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(detailsIntent)
            getPendingIntent(uniqueId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notificationText = if (!userName.isNullOrBlank()) {
            "$userName needs help. Click for more details."
        } else {
            body ?: "A person you are guarding needs help. Click for more details."
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: "Guardian Alert")
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(detailsPendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))

        if (!mobileNumber.isNullOrBlank()) {
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobileNumber"))
            val callPendingIntent = PendingIntent.getActivity(this, uniqueId + 1, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action(0, "Call", callPendingIntent))
        }

        manager.notify(uniqueId, builder.build())
    }
}
