
package com.sriox.vasatey

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentProfileBinding
import com.sriox.vasatey.models.User // Assuming you have a User data class
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val client = SupabaseInstance.client

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()

        binding.updateProfileButton.setOnClickListener {
            val newName = binding.nameInput.text.toString().trim()
            val newMobile = binding.mobileInput.text.toString().trim()

            if (newName.isNotEmpty() && newMobile.isNotEmpty()) {
                updateUserProfile(newName, newMobile)
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.changePasswordButton.setOnClickListener {
            showPasswordResetConfirmationDialog()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = client.auth.currentUserOrNull()
                if (user != null && user.email != null) {
                    val userData = client.from("users").select(columns = Columns.ALL) {
                        filter {
                            eq("email", user.email!!)
                        }
                    }.decodeSingle<User>()

                    binding.nameInput.setText(userData.username)
                    binding.emailInput.setText(userData.email)
                    binding.mobileInput.setText(userData.mobileNumber)
                    binding.emailInput.isEnabled = false
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to load user data", e)
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserProfile(newName: String, newMobile: String) {
        lifecycleScope.launch {
            try {
                val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
                
                val updates = mapOf(
                    "username" to newName,
                    "mobile_number" to newMobile
                )

                client.from("users").update(updates) {
                    filter {
                        eq("email", userEmail)
                    }
                }
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to update profile", e)
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPasswordResetConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setMessage("Are you sure you want to send a password reset email?")
            .setPositiveButton("Yes") { _, _ ->
                sendPasswordResetEmail()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun sendPasswordResetEmail() {
        lifecycleScope.launch {
            try {
                val userEmail = client.auth.currentUserOrNull()?.email
                if (userEmail != null) {
                    client.auth.resetPasswordForEmail(userEmail)
                    Toast.makeText(requireContext(), "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Could not find user email.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to send reset email", e)
                Toast.makeText(requireContext(), "Failed to send password reset email.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
