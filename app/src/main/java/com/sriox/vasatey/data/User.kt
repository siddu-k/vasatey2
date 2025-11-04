package com.sriox.vasatey.data

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val createdAt: String,
    val username: String? = null
)
