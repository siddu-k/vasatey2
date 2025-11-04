package com.sriox.vasatey

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentGuardianSettingsBinding
import com.sriox.vasatey.models.User
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresChangeFilter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class GuardianSettingsFragment : Fragment() {

    private var _binding: FragmentGuardianSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var guardiansAdapter: ArrayAdapter<String>
    private val guardiansList = mutableListOf<String>()

    private val client = SupabaseInstance.client
    private lateinit var realtimeChannel: RealtimeChannel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGuardianSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        guardiansAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, guardiansList)
        binding.guardiansListView.adapter = guardiansAdapter

        binding.addGuardianButton.setOnClickListener {
            val email = binding.guardianEmailInput.text.toString().trim()
            if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                updateGuardians(email, isAdding = true)
            } else {
                Toast.makeText(requireContext(), "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
        }

        binding.removeGuardianButton.setOnClickListener {
            val email = binding.guardianEmailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                updateGuardians(email, isAdding = false)
            }
        }

        subscribeToGuardianUpdates()
    }

    private fun updateGuardians(email: String, isAdding: Boolean) {
        lifecycleScope.launch {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
            try {
                val user = client.from("users").select() { filter { eq("email", userEmail) } }.decodeSingle<User>()
                val currentGuardians = user.guardians.toMutableList()

                if (isAdding) {
                    if (currentGuardians.contains(email)) {
                        Toast.makeText(requireContext(), "Guardian already exists", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    currentGuardians.add(email)
                } else {
                    if (!currentGuardians.contains(email)) {
                        Toast.makeText(requireContext(), "Guardian not found in your list", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    currentGuardians.remove(email)
                }

                val updates = mapOf("guardians" to currentGuardians)
                client.from("users").update(updates) { filter { eq("email", userEmail) } }

                val actionText = if (isAdding) "added" else "removed"
                Toast.makeText(requireContext(), "Guardian $actionText successfully", Toast.LENGTH_SHORT).show()
                binding.guardianEmailInput.text.clear()

            } catch (e: Exception) {
                Log.e("GuardianSettings", "Failed to update guardians", e)
                Toast.makeText(requireContext(), "Operation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun subscribeToGuardianUpdates() {
        val userEmail = client.auth.currentUserOrNull()?.email ?: return

        realtimeChannel = client.channel("guardians-for-$userEmail")
        lifecycleScope.launch {
            fetchInitialGuardians(userEmail)

            realtimeChannel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "users"
                filter = PostgresChangeFilter(
                    column = "email",
                    operator = "eq",
                    value = userEmail
                )
            }.catch { cause ->
                Log.e("GuardianSettings", "Realtime listener error", cause)
            }.collect { update ->
                val updatedUser = Json.decodeFromJsonElement<User>(update.record)
                activity?.runOnUiThread {
                    guardiansList.clear()
                    guardiansList.addAll(updatedUser.guardians)
                    guardiansAdapter.notifyDataSetChanged()
                }
            }
        }
        lifecycleScope.launch {
            realtimeChannel.subscribe()
        }
    }

    private suspend fun fetchInitialGuardians(userEmail: String) {
        try {
            val user = client.from("users").select() { filter { eq("email", userEmail) } }.decodeSingle<User>()
            activity?.runOnUiThread {
                guardiansList.clear()
                guardiansList.addAll(user.guardians)
                guardiansAdapter.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            Log.e("GuardianSettings", "Failed to fetch initial guardians", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            if (::realtimeChannel.isInitialized) {
                realtimeChannel.unsubscribe()
            }
        }
        _binding = null
    }
}
