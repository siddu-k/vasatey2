package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SplashActivity : AppCompatActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The new way to check session status with SDK v2.x
        SupabaseInstance.client.auth.sessionStatus
            .onEach { status ->
                if (navigated) return@onEach // Prevent multiple navigations

                when (status) {
                    is SessionStatus.Authenticated -> {
                        navigated = true
                        // User is logged in, update token and go to Main
                        lifecycleScope.launch{
                            updateFcmToken(status.session.user?.email)
                        }
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        navigated = true
                        // Not logged in → go to Login
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                        finish()
                    }
                    // Handle other states if necessary (e.g., LoadingFromStorage)
                    else -> { /* Do nothing, wait for a definitive status */ }
                }
            }
            .launchIn(lifecycleScope) // The flow collector needs to be launched in a coroutine scope
    }

    private suspend fun updateFcmToken(userEmail: String?) {
        if (userEmail == null) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val updates = mapOf("fcm_token" to token)

            SupabaseInstance.client.from("users").update(updates) {
                filter {
                    eq("email", userEmail)
                }
            }
            Log.d("FCM", "FCM token refreshed and updated in Supabase.")
        } catch (e: Exception) {
            Log.w("FCM", "Error updating FCM token during splash", e)
        }
    }
}
