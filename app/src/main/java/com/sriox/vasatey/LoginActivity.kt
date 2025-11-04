
package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.databinding.ActivityLoginBinding
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.EmailPassword
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            handleLogin()
        }

        binding.signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun handleLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Sign in with Supabase
                SupabaseInstance.client.auth.signInWith(EmailPassword) {
                    this.email = email
                    this.password = password
                }

                // On success, update FCM token and proceed
                getAndSaveFcmToken(email)

                Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()

            } catch (e: Exception) {
                Log.e("LoginActivity", "Login failed", e)
                Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun getAndSaveFcmToken(userEmail: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            
            val updates = mapOf("fcm_token" to token)

            SupabaseInstance.client.from("users").update(updates) {
                filter {
                    eq("email", userEmail)
                }
            }
            Log.d("FCM", "FCM token updated successfully in Supabase.")
        } catch (e: Exception) {
            Log.w("FCM", "Error updating FCM token in Supabase", e)
        }
    }
}
