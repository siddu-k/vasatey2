
package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.databinding.ActivitySignupBinding
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signupBtn.setOnClickListener {
            handleSignup()
        }

        binding.loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun handleSignup() {
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val mobileNumber = binding.mobileInput.text.toString().trim()
        val school = binding.schoolInput.text.toString().trim()
        val pet = binding.petInput.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || mobileNumber.isEmpty() || school.isEmpty() || pet.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Get FCM token first
                val fcmToken = FirebaseMessaging.getInstance().token.await()

                // Sign up the user with Supabase, embedding metadata
                SupabaseInstance.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data = buildJsonObject { 
                        put("username", name)
                        put("mobile_number", mobileNumber)
                        put("fcm_token", fcmToken)
                        putJsonObject("security_questions") {
                            put("school", school)
                            put("pet", pet)
                        }
                    }
                }

                // After signup, Supabase sends a confirmation email.
                // Inform the user and redirect to login.
                Toast.makeText(this@SignupActivity, "Signup successful! Please check your email to confirm your account.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                finish()

            } catch (e: Exception) {
                Log.e("SignupActivity", "Signup failed", e)
                Toast.makeText(this@SignupActivity, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
