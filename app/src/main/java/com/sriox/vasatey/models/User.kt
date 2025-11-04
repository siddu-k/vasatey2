
package com.sriox.vasatey.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class User(
    val id: String, // Matches the user's UUID from Supabase auth

    @SerialName("username")
    val username: String,

    val email: String,

    @SerialName("mobile_number")
    val mobileNumber: String,

    @SerialName("fcm_token")
    val fcmToken: String? = null,

    @SerialName("access_key")
    val accessKey: String? = null,
    
    @SerialName("wake_word")
    val wakeWord: String? = null,

    // Using JsonObject for flexible JSONB column
    @SerialName("security_questions")
    val securityQuestions: JsonObject? = null,

    // List of guardian emails
    val guardians: List<String> = emptyList(),

    @SerialName("created_at")
    val createdAt: String? = null
)
