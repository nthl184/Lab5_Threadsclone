package com.example.lab5

import com.google.firebase.Timestamp

data class AppNotification(
    val id: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val message: String = "",
    val postId: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp? = null
)