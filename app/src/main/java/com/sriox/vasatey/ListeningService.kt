
package com.sriox.vasatey

import ai.picovoice.porcupine.*
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sriox.vasatey.models.Alert
import com.sriox.vasatey.models.User
import com.sriox.vasatey.models.VercelNotificationRequest
import com.sriox.vasatey.network.RetrofitInstance
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ListeningService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val client = SupabaseInstance.client

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        acquireWakeLock()
        startForegroundService()
        startPorcupineListening()
    }

    private fun startPorcupineListening() {
        serviceScope.launch {
            try {
                val user = getUserFromSupabase()
                if (user?.accessKey == null) {
                    showErrorNotification("Picovoice Access Key not found in your profile.")
                    stopSelf()
                    return@launch
                }

                val wakeWord = user.wakeWord ?: "help-me"
                val keywordFileName = if (wakeWord == "leave-me-alone") "leave-me-alone.ppn" else "help.ppn"
                val keywordPath = FileUtils.extractAsset(this@ListeningService, keywordFileName)

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(user.accessKey)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(0.7f)
                    .build(applicationContext) { _ ->
                        Log.d("ListeningService", "Wake word detected.")
                        triggerHelpAlertToGuardians()
                    }

                porcupineManager?.start()
                Log.d("ListeningService", "Porcupine started successfully for '$wakeWord'.")

            } catch (e: Exception) {
                Log.e("ListeningService", "Error starting porcupine", e)
                showErrorNotification("A critical error occurred in the listening service.")
                stopSelf()
            }
        }
    }

    private suspend fun getUserFromSupabase(): User? {
        return try {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return null
            client.from("users").select() { filter { eq("email", userEmail) } }.decodeSingle<User>()
        } catch (e: Exception) {
            Log.e("ListeningService", "Failed to get user from Supabase", e)
            null
        }
    }

    private fun triggerHelpAlertToGuardians() = serviceScope.launch {
        val location = getCurrentLocation()
        if (location == null) {
            Log.w("ListeningService", "Proceeding with alert but without location data.")
        }

        val user = getUserFromSupabase() ?: return@launch
        if (user.guardians.isEmpty()) {
            showErrorNotification("You have no guardians to alert.")
            return@launch
        }

        val notificationTasks = user.guardians.map { guardianEmail ->
            async(Dispatchers.IO) {
                sendNotificationToGuardian(
                    guardianEmail,
                    user.username,
                    user.email,
                    user.mobileNumber,
                    location?.latitude,
                    location?.longitude
                )
            }
        }

        val results = notificationTasks.awaitAll()
        val successCount = results.count { it }

        withContext(Dispatchers.Main) {
            if (successCount > 0) {
                showDetectedNotification(successCount, user.guardians.size)
            } else {
                showErrorNotification("Help alert failed to send. Check connection.")
            }
        }
    }

    private suspend fun sendNotificationToGuardian(guardianEmail: String, fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?): Boolean {
        return try {
            val guardian = client.from("users").select() { filter { eq("email", guardianEmail) } }.decodeSingle<User>()
            val guardianToken = guardian.fcmToken

            if (guardianToken == null) {
                Log.w("ListeningService", "Guardian $guardianEmail has no FCM token.")
                return false
            }

            val request = VercelNotificationRequest(guardianToken, "vasatey alert", "$fromUserName needs help", fromUserName, fromUserEmail, fromUserMobile, lat, lon)
            val response = RetrofitInstance.api.sendNotification(request)
            
            if (response.isSuccessful) {
                logAlertToSupabase(guardianEmail, fromUserName, fromUserEmail, fromUserMobile, lat, lon)
                true
            } else {
                Log.w("VercelError", "Failed to send notification via Vercel: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("ListeningService", "Error sending notification to $guardianEmail", e)
            false
        }
    }

    private suspend fun logAlertToSupabase(guardianEmail: String, fromUserName: String, fromUserEmail: String, fromUserMobile: String?, lat: Double?, lon: Double?) {
        try {
            val alert = Alert(
                guardianEmail = guardianEmail,
                fromUserName = fromUserName,
                fromUserEmail = fromUserEmail,
                fromUserMobile = fromUserMobile,
                latitude = lat,
                longitude = lon
            )
            client.from("alerts").insert(alert)
        } catch (e: Exception) {
            Log.e("ListeningService", "Failed to log alert to Supabase", e)
        }
    }
    
    // --- Lifecycle, Permissions, and Notification methods remain largely the same ---

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: Exception) {
            Log.e("ListeningService", "Could not get location", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        porcupineManager?.stop()
        porcupineManager?.delete()
        serviceScope.cancel()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vasatey::listening_wake_lock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "vasatey_listen_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Vasatey Listening Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Vasatey")
            .setContentText("Listening for wake word…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(2, notification)
    }

    private fun showDetectedNotification(successCount: Int, totalCount: Int) {
        val channelId = "vasatey_alert_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Help Alerts Sent", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Help Alert Sent!")
            .setContentText("Notified $successCount of $totalCount guardians.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showErrorNotification(message: String) {
        val channelId = "vasatey_error_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Service Errors", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vasatey Alert Error")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
