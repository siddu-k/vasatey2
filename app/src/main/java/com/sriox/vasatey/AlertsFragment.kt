package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentAlertsBinding
import com.sriox.vasatey.models.Alert
import com.sriox.vasatey.network.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.text.SimpleDateFormat
import java.util.*

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val client = SupabaseInstance.client
    private lateinit var alertsAdapter: ArrayAdapter<String>
    private val alertsList = mutableListOf<Alert>()
    private lateinit var realtimeChannel: RealtimeChannel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1)
        binding.alertsListView.adapter = alertsAdapter

        binding.alertsListView.setOnItemClickListener { _, _, position, _ ->
            if (position < alertsList.size) {
                val clickedAlert = alertsList[position]
                val intent = Intent(requireContext(), AlertDetailsActivity::class.java).apply {
                    putExtra("USER_NAME", clickedAlert.fromUserName)
                    putExtra("USER_EMAIL", clickedAlert.fromUserEmail)
                    putExtra("USER_MOBILE", clickedAlert.fromUserMobile)
                    putExtra("USER_LATITUDE", clickedAlert.latitude.toString())
                    putExtra("USER_LONGITUDE", clickedAlert.longitude.toString())
                }
                startActivity(intent)
            }
        }

        subscribeToAlerts()
    }

    private fun subscribeToAlerts() {
        val currentUserEmail = client.auth.currentUserOrNull()?.email ?: return

        realtimeChannel = client.channel("alerts-for-$currentUserEmail")

        lifecycleScope.launch {
            fetchInitialAlerts(currentUserEmail)

            // ✅ Correct filter usage in Supabase 3.x
            val changeFlow = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(
                schema = "public"
            ) {
                table = "alerts"
                filter {
                    eq("guardian_email", currentUserEmail)
                }
            }

            changeFlow
                .catch { cause ->
                    Log.e("AlertsFragment", "Realtime listener error", cause)
                }
                .collect { insert ->
                    val newAlert = Json.decodeFromJsonElement<Alert>(insert.record)
                    activity?.runOnUiThread {
                        alertsList.add(0, newAlert)
                        updateAdapterWithFormattedText()
                    }
                }
        }

        lifecycleScope.launch {
            realtimeChannel.subscribe()
        }
    }

    private suspend fun fetchInitialAlerts(email: String) {
        try {
            val initialAlerts = client.from("alerts")
                .select {
                    filter { eq("guardian_email", email) }
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<Alert>()

            alertsList.clear()
            alertsList.addAll(initialAlerts)
            updateAdapterWithFormattedText()
        } catch (e: Exception) {
            Log.e("AlertsFragment", "Failed to fetch initial alerts", e)
        }
    }

    private fun updateAdapterWithFormattedText() {
        val displayList = alertsList.map {
            val formattedDate = it.createdAt?.let { ts -> formatTimestamp(ts) } ?: "Just now"
            "${it.fromUserName}, ${it.fromUserMobile ?: "No number"}, $formattedDate - Tap for details"
        }
        activity?.runOnUiThread {
            alertsAdapter.clear()
            alertsAdapter.addAll(displayList)
            alertsAdapter.notifyDataSetChanged()
        }
    }

    private fun formatTimestamp(timestamp: String): String {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        val outputFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        for (pattern in patterns) {
            try {
                val inputFormat = SimpleDateFormat(pattern, Locale.getDefault())
                inputFormat.parse(timestamp)?.let {
                    return outputFormat.format(it)
                }
            } catch (_: Exception) { }
        }
        return timestamp
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
