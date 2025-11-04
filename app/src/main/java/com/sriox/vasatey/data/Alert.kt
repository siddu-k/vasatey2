package com.sriox.vasatey.data

import kotlinx.serialization.Serializable

@Serializable
data class Alert(
    val id: Long? = null,
    val createdAt: String? = null,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val userId: String
)
