package com.sriox.vasatey.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseInstance {

    private const val SUPABASE_URL = "https://hjxmjmdqvgiaeourpbbc.supabase.co"
    private const val SUPABASE_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ"

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(GoTrue) {}
        install(Postgrest) {}
        install(Realtime) {}
        install(Storage) {}
        install(Functions) {}
    }
}
