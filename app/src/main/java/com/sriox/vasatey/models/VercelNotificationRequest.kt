package com.sriox.vasatey.models

data class VercelNotificationRequest(
    val token: String,
    val title: String,
    val body: String,
    val username: String,
    val email: String,
    val mobileNumber: String?,
    val latitude: Double?,
    val longitude: Double?
)
