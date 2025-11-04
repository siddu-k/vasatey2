
package com.sriox.vasatey

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentHelpSettingsBinding
import com.sriox.vasatey.models.User
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HelpSettingsFragment : Fragment() {

    private var _binding: FragmentHelpSettingsBinding? = null
    private val binding get() = _binding!!

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val client = SupabaseInstance.client
    private var isProgrammaticCheck = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHelpSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadInitialSettings()

        binding.startButton.setOnClickListener {
            if (binding.accessKeyInput.text.toString().isEmpty()) {
                showAccessKeyAlert()
            } else if (!isIgnoringBatteryOptimizations()) {
                showBatteryOptimizationDialog()
            } else if (hasPermissions()) {
                startListeningService()
            } else {
                requestPermissions.launch(permissions)
            }
        }

        binding.stopButton.setOnClickListener { stopListeningService() }
        binding.saveAccessKeyButton.setOnClickListener {
            val accessKey = binding.accessKeyInput.text.toString().trim()
            if (accessKey.isNotEmpty()) saveAccessKey(accessKey)
        }
        binding.deleteAccessKeyButton.setOnClickListener { deleteAccessKey() }

        binding.wakeWordRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isProgrammaticCheck) return@setOnCheckedChangeListener

            val selectedWakeWord = if (checkedId == R.id.helpMeRadioButton) "help-me" else "leave-me-alone"
            saveWakeWordPreference(selectedWakeWord)

            if (isServiceRunning(ListeningService::class.java)) {
                Toast.makeText(requireContext(), "Restarting listener with new wake word...", Toast.LENGTH_LONG).show()
                CoroutineScope(Dispatchers.Main).launch {
                    stopListeningService(false)
                    delay(500)
                    startListeningService()
                }
            }
        }
    }

    private fun loadInitialSettings() {
        lifecycleScope.launch {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
            try {
                val user = client.from("users").select() { filter { eq("email", userEmail) } }.decodeSingle<User>()
                binding.accessKeyInput.setText(user.accessKey)
                isProgrammaticCheck = true
                if (user.wakeWord == "leave-me-alone") {
                    binding.wakeWordRadioGroup.check(R.id.leaveMeAloneRadioButton)
                } else {
                    binding.wakeWordRadioGroup.check(R.id.helpMeRadioButton)
                }
                isProgrammaticCheck = false
            } catch (e: Exception) {
                Log.e("HelpSettings", "Failed to load settings", e)
            }
        }
    }

    private fun saveAccessKey(accessKey: String) {
        lifecycleScope.launch {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
            try {
                client.from("users").update(mapOf("access_key" to accessKey)) { filter { eq("email", userEmail) } }
                Toast.makeText(requireContext(), "Access Key saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to save key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAccessKey() {
        lifecycleScope.launch {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
            try {
                client.from("users").update(mapOf("access_key" to null)) { filter { eq("email", userEmail) } }
                Toast.makeText(requireContext(), "Access Key deleted", Toast.LENGTH_SHORT).show()
                binding.accessKeyInput.text?.clear()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to delete key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveWakeWordPreference(wakeWord: String) {
        lifecycleScope.launch {
            val userEmail = client.auth.currentUserOrNull()?.email ?: return@launch
            try {
                client.from("users").update(mapOf("wake_word" to wakeWord)) { filter { eq("email", userEmail) } }
            } catch (e: Exception) {
                 Log.e("HelpSettings", "Failed to save wake word", e)
            }
        }
    }
    
    // --- The rest of the functions (permissions, service management, dialogs) remain the same ---

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { serviceClass.name == it.service.className }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else {
            true
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable Background Operation")
            .setMessage("For the help alert to work reliably, please allow the app to ignore battery optimizations.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:" + requireContext().packageName)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) startListeningService()
    }

    private fun hasPermissions(): Boolean = permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }

    private fun startListeningService() {
        val intent = Intent(requireContext(), ListeningService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        Toast.makeText(requireContext(), "Listening started", Toast.LENGTH_SHORT).show()
    }

    private fun stopListeningService(showToast: Boolean = true) {
        val intent = Intent(requireContext(), ListeningService::class.java)
        requireContext().stopService(intent)
        if (showToast) Toast.makeText(requireContext(), "Listening stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showAccessKeyAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle("Access Key Required")
            .setMessage("Please add your Picovoice Access Key to start listening.")
            .setPositiveButton("Get Key") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai/login")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
