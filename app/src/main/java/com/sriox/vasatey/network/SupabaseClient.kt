package com.sriox.vasatey.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {

    private const val SUPABASE_URL = "YOUR_SUPABASE_URL" // TODO: Replace with your Supabase project URL
    private const val SUPABASE_KEY = "YOUR_SUPABASE_ANON_KEY" // TODO: Replace with your Supabase anon key

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
